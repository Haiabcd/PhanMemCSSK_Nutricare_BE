package com.hn.nutricarebe.service.impl;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.Year;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.Locale;
import java.util.stream.Collectors;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.hn.nutricarebe.dto.response.ProfileDto;
import com.hn.nutricarebe.dto.response.RecoItemDto;
import com.hn.nutricarebe.entity.Profile;
import com.hn.nutricarebe.entity.UserAllergy;
import com.hn.nutricarebe.entity.UserCondition;
import com.hn.nutricarebe.exception.AppException;
import com.hn.nutricarebe.exception.ErrorCode;
import com.hn.nutricarebe.repository.ProfileRepository;
import com.hn.nutricarebe.repository.UserAllergyRepository;
import com.hn.nutricarebe.repository.UserConditionRepository;
import com.hn.nutricarebe.service.RecommendationService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RecommendationServiceImpl implements RecommendationService {
    ProfileRepository profileRepository;
    UserAllergyRepository userAllergyRepository;
    UserConditionRepository userConditionRepository;

    /* ====== Một số nguồn RSS VN ổn định (bổ sung cho Google News) ====== */
    private static final List<String> ARTICLE_FEEDS = Arrays.asList(
            "https://vnexpress.net/rss/suc-khoe/dinh-duong.rss",
            "https://vnexpress.net/rss/suc-khoe/van-dong.rss",
            "https://tuoitre.vn/rss/suc-khoe.rss",
            "https://znews.vn/rss/suc-khoe.rss",
            "https://vtc.vn/rss/song-khoe.rss",
            "https://phunuvietnam.vn/rss/dinh-duong.rss");

    /* ========================= Google News RSS ========================= */
    private static String googleNewsRssUrl(String query, String lang, String country) {
        String q = Optional.ofNullable(query).orElse("").trim().replaceAll("\\s+", " ");
        if (q.length() > 200) q = q.substring(0, 200);
        String enc = URLEncoder.encode(q, StandardCharsets.UTF_8);
        String hl = (lang == null || lang.isEmpty()) ? "vi" : lang;
        String gl = (country == null || country.isEmpty()) ? "VN" : country;
        String ceid = gl + ":" + hl;
        return "https://news.google.com/rss/search?q=" + enc + "&hl=" + hl + "&gl=" + gl + "&ceid=" + ceid;
    }

    private List<RecoItemDto> fetchGoogleNews(String query) throws Exception {
        String url = googleNewsRssUrl(query, "vi", "VN");
        Document doc = fetchDoc(url);

        List<Element> items = doc.select("item");
        List<RecoItemDto> out = new ArrayList<>();

        for (Element it : items) {
            String title = text(it, "title");
            String link = text(it, "link");
            String pub = text(it, "pubDate");
            Element src = it.selectFirst("source");
            String sourceName = (src != null) ? src.text() : hostOf(link);
            String img = firstImgFromHtml(text(it, "description"));

            if (isBlank(link) || isBlank(title)) continue;

            // BỎ các link video/clip
            if (isVideoLike(link, sourceName, title)) continue;

            RecoItemDto dto = new RecoItemDto();
            dto.setType("article");
            dto.setTitle(title);
            dto.setUrl(link);
            dto.setSource(sourceName);
            dto.setImageUrl(img);
            dto.setPublished(parsePub(pub));
            out.add(dto);
        }
        return out;
    }

    /* ========================= PubMed (tin nghiên cứu) ========================= */
    private static String pubmedSearchUrl(String query) {
        return "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi" + "?db=pubmed&retmode=json&retmax=10&term="
                + URLEncoder.encode(query, StandardCharsets.UTF_8);
    }

    @Override
    public List<RecoItemDto> find(int limit) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) throw new AppException(ErrorCode.UNAUTHORIZED);
        UUID userId = UUID.fromString(auth.getName());

        Optional<Profile> p = profileRepository.findByUser_Id(userId);

        // vẫn đọc allergy/condition từ DB nhưng KHÔNG dùng allergy để chặn tin
        List<UserAllergy> la = userAllergyRepository.findByUser_Id(userId);
        List<UserCondition> lc = userConditionRepository.findByUser_Id(userId);

        List<String> allergyNames = la.stream()
                .map(ua -> ua.getAllergy() != null ? ua.getAllergy().getName() : null)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList());

        List<String> conditionNames = lc.stream()
                .map(uc -> uc.getCondition() != null ? uc.getCondition().getName() : null)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList());

        ProfileDto profile = p.map(pr -> ProfileDto.builder()
                        .goal(mapGoalToString(pr.getGoal()))
                        .activity(mapActivityToString(pr.getActivityLevel()))
                        .age(calcAge(pr.getBirthYear()))
                        .heightCm(
                                pr.getHeightCm() == null
                                        ? null
                                        : pr.getHeightCm().doubleValue())
                        .weightKg(
                                pr.getWeightKg() == null
                                        ? null
                                        : pr.getWeightKg().doubleValue())
                        .conditions(conditionNames)
                        .allergies(allergyNames)
                        .locale("vi")
                        .build())
                .orElseThrow(() -> new AppException(ErrorCode.PROFILE_NOT_FOUND));

        int lim = clampLimit(limit);

        /* ===================== Thu thập dữ liệu thô ===================== */
        List<RecoItemDto> items = new ArrayList<>();

        // Query cho tin tức
        String generalQuery = buildGeneralNewsQuery();
        String goalQuery = buildGoalNewsQuery(profile);
        List<String> condQueries = buildConditionNewsQueries(profile);

        // 1) Google News theo goal + conditions + general
        try {
            items.addAll(fetchGoogleNews(goalQuery));
        } catch (Exception e) {
            System.err.println("[GN goal] " + e.getMessage());
        }
        for (String q : condQueries) {
            try {
                items.addAll(fetchGoogleNews(q));
            } catch (Exception e) {
                System.err.println("[GN cond] " + e.getMessage());
            }
        }
        try {
            items.addAll(fetchGoogleNews(generalQuery));
        } catch (Exception e) {
            System.err.println("[GN general] " + e.getMessage());
        }

        // 2) RSS báo VN (bổ sung coverage)
        for (String rss : ARTICLE_FEEDS) {
            try {
                items.addAll(fetchRssArticles(rss));
            } catch (Exception e) {
                System.err.println("[RSS ERR] " + rss + " -> " + e.getMessage());
            }
        }

        // 3) PubMed (tin nghiên cứu/khuyến cáo khoa học) với query tiếng Anh
        try {
            items.addAll(fetchPubMed(buildEnglishQuery(profile)));
        } catch (Exception e) {
            System.err.println("[PM ERR] " + e.getMessage());
        }

        /* ===================== Lọc nội dung & Dedupe ===================== */
        List<String> nutritionKeywords = nutritionKeywords();
        List<String> banned = sensitiveKeywords();

        items = items.stream()
                // 1) phải liên quan dinh dưỡng/sức khỏe
                .filter(it -> {
                    String hay = (safeLower(it.getTitle()) + " " + safeLower(it.getSource())).trim();
                    return containsAny(hay, nutritionKeywords);
                })
                // 2) loại bài có từ nhạy cảm (vd: giòi, ấu trùng…)
                .filter(it -> !containsAny(safeLower(it.getTitle()), banned))
                .collect(Collectors.toList());

        // dedupe
        items = dedupeArticles(items);

        /* ===================== ƯU TIÊN: goal/conditions ===================== */
        List<String> goalBoostKws = goalBoostKeywords(profile);
        List<String> condBoostKws = conditionBoostKeywords(profile);

        List<RecoItemDto> priority = new ArrayList<>();
        List<RecoItemDto> others = new ArrayList<>();

        for (RecoItemDto it : items) {
            String hay = (safeLower(it.getTitle()) + " " + safeLower(it.getSource())).trim();
            boolean hitGoal = containsAny(hay, goalBoostKws);
            boolean hitCond = containsAny(hay, condBoostKws);
            if (hitGoal || hitCond) {
                priority.add(it);
            } else {
                others.add(it);
            }
        }

        /* ===================== Scoring & Sorting ===================== */
        Comparator<RecoItemDto> priorityCmp = (a, b) -> {
            double sa = 1.3 * recencyBoost(a.getPublished())
                    + domainBoost(a.getSource())
                    + 0.8 * relevanceScore(safeLower(a.getTitle()), merge(goalBoostKws, condBoostKws));
            double sb = 1.3 * recencyBoost(b.getPublished())
                    + domainBoost(b.getSource())
                    + 0.8 * relevanceScore(safeLower(b.getTitle()), merge(goalBoostKws, condBoostKws));
            int c = Double.compare(sb, sa);
            if (c != 0) return c;
            return comparePublishedDesc(a.getPublished(), b.getPublished());
        };

        Comparator<RecoItemDto> othersCmp = (a, b) -> {
            double sa = 1.2 * recencyBoost(a.getPublished())
                    + domainBoost(a.getSource())
                    + 0.3 * relevanceScore(safeLower(a.getTitle()), nutritionKeywords);
            double sb = 1.2 * recencyBoost(b.getPublished())
                    + domainBoost(b.getSource())
                    + 0.3 * relevanceScore(safeLower(b.getTitle()), nutritionKeywords);
            int c = Double.compare(sb, sa);
            if (c != 0) return c;
            return comparePublishedDesc(a.getPublished(), b.getPublished());
        };

        priority.sort(priorityCmp);
        others.sort(othersCmp);

        /* ===================== Gộp kết quả ===================== */
        List<RecoItemDto> result = new ArrayList<>(lim);
        for (RecoItemDto it : priority) {
            if (result.size() >= lim) break;
            result.add(it);
        }
        if (result.size() < lim) {
            for (RecoItemDto it : others) {
                if (result.size() >= lim) break;
                if (!containsKey(result, keyOf(it))) result.add(it);
            }
        }
        return result;
    }

    /* ============================ Helpers dành cho NEWS ============================ */

    /** Query chung về dinh dưỡng/sức khỏe để lấp đầy */
    private static String buildGeneralNewsQuery() {
        List<String> parts = new ArrayList<>(List.of(
                "dinh dưỡng",
                "sức khỏe",
                "khuyến cáo",
                "cảnh báo",
                "ăn kiêng",
                "thực phẩm",
                "chế độ ăn",
                "bác sĩ",
                "viện dinh dưỡng",
                "WHO"));
        return join(parts, " ");
    }

    /** Query tập trung mục tiêu */
    private static String buildGoalNewsQuery(ProfileDto p) {
        List<String> parts = new ArrayList<>();
        if (p != null && !isBlank(p.getGoal())) parts.add(p.getGoal());
        parts.addAll(Arrays.asList("dinh dưỡng", "sức khỏe", "thực phẩm", "chế độ ăn", "ăn kiêng"));
        return join(parts, " ");
    }

    /** Gộp danh sách từ khóa, bỏ trùng */
    private static List<String> merge(List<String> a, List<String> b) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        if (a != null) {
            for (String s : a) {
                if (s == null) continue;
                String t = s.trim().toLowerCase(Locale.ROOT);
                if (!t.isEmpty()) set.add(t);
            }
        }
        if (b != null) {
            for (String s : b) {
                if (s == null) continue;
                String t = s.trim().toLowerCase(Locale.ROOT);
                if (!t.isEmpty()) set.add(t);
            }
        }
        return new ArrayList<>(set);
    }

    /** haystack có chứa BẤT KỲ từ khóa nào? */
    private static boolean containsAny(String haystack, List<String> kws) {
        if (haystack == null || haystack.trim().isEmpty() || kws == null || kws.isEmpty()) return false;
        String s = haystack.toLowerCase(Locale.ROOT);
        for (String k : kws) {
            if (k == null) continue;
            String t = k.trim().toLowerCase(Locale.ROOT);
            if (!t.isEmpty() && s.contains(t)) return true;
        }
        return false;
    }

    /** Mỗi bệnh nền thành một query riêng */
    private static List<String> buildConditionNewsQueries(ProfileDto p) {
        if (p == null || p.getConditions() == null || p.getConditions().isEmpty()) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        for (String c : p.getConditions()) {
            if (isBlank(c)) continue;
            out.add(join(Arrays.asList(c, "dinh dưỡng", "sức khỏe", "thực phẩm", "chế độ ăn"), " "));
        }
        return out;
    }

    /** Từ khóa xác định tin dinh dưỡng/sức khỏe */
    private static List<String> nutritionKeywords() {
        return Arrays.asList(
                // VI
                "dinh dưỡng",
                "sức khỏe",
                "thực phẩm",
                "ăn kiêng",
                "chế độ ăn",
                "khuyến cáo",
                "cảnh báo",
                "bác sĩ",
                "bệnh viện",
                "viện dinh dưỡng",
                "vitamin",
                "khoáng chất",
                "đạm",
                "protein",
                "carb",
                "chất béo",
                "cholesterol",
                "đái tháo đường",
                "tiểu đường",
                "huyết áp",
                "mỡ máu",
                "tim mạch",
                "béo phì",
                // EN
                "nutrition",
                "diet",
                "healthy",
                "meal",
                "intake",
                "nutrient",
                "vitamin",
                "mineral",
                "cholesterol",
                "hypertension",
                "diabetes",
                "obesity",
                "cardio",
                "heart");
    }

    /** TỪ NHẠY CẢM cần lọc bỏ (VD: “giòi”) */
    private static List<String> sensitiveKeywords() {
        return Arrays.asList("con dòi", "giòi", "dòi", "ấu trùng", "bọ gậy", "thối rữa", "ruồi bọ");
    }

    /** Tạo boost keywords theo goal */
    private static List<String> goalBoostKeywords(ProfileDto p) {
        List<String> out = new ArrayList<>();
        if (p == null || isBlank(p.getGoal())) return out;
        String g = p.getGoal().toLowerCase(Locale.ROOT);
        if (g.contains("tăng cơ") || g.contains("tăng cân")) {
            Collections.addAll(
                    out,
                    "tăng cân",
                    "tăng cơ",
                    "hypertrophy",
                    "build muscle",
                    "muscle",
                    "protein cao",
                    "high protein",
                    "lean mass");
        } else if (g.contains("giảm")) {
            Collections.addAll(
                    out,
                    "giảm cân",
                    "giảm mỡ",
                    "đốt mỡ",
                    "weight loss",
                    "fat loss",
                    "low calorie",
                    "calo thấp",
                    "calorie deficit");
        } else if (g.contains("giữ cân")) {
            Collections.addAll(out, "giữ cân", "duy trì", "balanced diet", "cân bằng", "maintenance");
        } else {
            out.add(g);
        }
        return out;
    }

    /** Tạo boost keywords theo bệnh nền */
    private static List<String> conditionBoostKeywords(ProfileDto p) {
        List<String> out = new ArrayList<>();
        if (p == null || p.getConditions() == null) return out;
        for (String c : p.getConditions()) {
            String lc = c == null ? "" : c.toLowerCase(Locale.ROOT);
            if (lc.contains("tiểu đường") || lc.contains("đái tháo đường") || lc.contains("diabetes")) {
                Collections.addAll(
                        out,
                        "tiểu đường",
                        "đái tháo đường",
                        "diabetes",
                        "đường huyết",
                        "glycemic",
                        "low glycemic",
                        "insulin");
            } else if (lc.contains("huyết áp") || lc.contains("hypertension")) {
                Collections.addAll(out, "huyết áp", "hypertension", "ít muối", "tim mạch", "heart", "sodium");
            } else if (lc.contains("mỡ máu") || lc.contains("cholesterol") || lc.contains("dyslipidemia")) {
                Collections.addAll(
                        out,
                        "cholesterol",
                        "mỡ máu",
                        "hdl",
                        "ldl",
                        "triglyceride",
                        "ít chất béo bão hòa",
                        "statin",
                        "heart");
            } else if (!isBlank(c)) {
                out.add(c);
            }
        }
        return out;
    }

    /* ========================= Fetchers & Common utils ========================= */

    private List<RecoItemDto> fetchRssArticles(String rssUrl) throws Exception {
        Document doc = fetchDoc(rssUrl);
        List<RecoItemDto> out = new ArrayList<>();

        List<Element> items = doc.select("item");
        boolean isAtom = false;
        if (items.isEmpty()) {
            items = doc.select("entry");
            isAtom = !items.isEmpty();
        }

        for (Element it : items) {
            String title, link, pub, img = null, source;

            if (!isAtom) {
                title = text(it, "title");
                link = text(it, "link");
                pub = text(it, "pubDate");
                Element mt = it.selectFirst("media|thumbnail, thumbnail");
                if (mt != null) img = mt.hasAttr("url") ? mt.attr("url") : mt.attr("href");
                if (img == null) img = firstImgFromHtml(text(it, "description"));
            } else {
                title = text(it, "title");
                Element linkEl = it.selectFirst("link");
                link = (linkEl != null) ? (linkEl.hasAttr("href") ? linkEl.attr("href") : linkEl.text()) : "";
                pub = text(it, "updated");
                if (pub.isEmpty()) pub = text(it, "published");
                Element mt = it.selectFirst("media|thumbnail, thumbnail");
                if (mt != null) img = mt.hasAttr("url") ? mt.attr("url") : mt.attr("href");
                if (img == null) img = firstImgFromHtml(text(it, "summary"));
            }

            if (link.trim().isEmpty()) continue;

            // BỎ các bài mang tính "video/clip"
            if (isVideoLike(link, hostOf(link), title)) continue;

            source = hostOf(link);
            Instant published = parsePub(pub);

            RecoItemDto dto = new RecoItemDto();
            dto.setType("article");
            dto.setTitle(title);
            dto.setUrl(link);
            dto.setSource(source);
            dto.setImageUrl(img);
            dto.setPublished(published);
            out.add(dto);
        }

        return out;
    }

    private List<RecoItemDto> fetchPubMed(String query) throws Exception {
        Document d1 = fetchDoc(pubmedSearchUrl(query));
        org.json.JSONObject search = new org.json.JSONObject(d1.text());
        org.json.JSONObject esr = search.optJSONObject("esearchresult");
        if (esr == null) return Collections.emptyList();
        org.json.JSONArray idList = esr.optJSONArray("idlist");
        if (idList == null || idList.isEmpty()) return Collections.emptyList();

        List<String> idsArr = new ArrayList<>();
        for (int i = 0; i < idList.length(); i++) {
            idsArr.add(String.valueOf(idList.get(i)));
        }
        String ids = join(idsArr, ",");

        String sumUrl =
                "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esummary.fcgi" + "?db=pubmed&retmode=json&id=" + ids;
        Document d2 = fetchDoc(sumUrl);
        org.json.JSONObject root = new org.json.JSONObject(d2.text());
        org.json.JSONObject sum = root.optJSONObject("result");
        if (sum == null) return Collections.emptyList();

        List<RecoItemDto> out = new ArrayList<>();
        String[] idSplit = ids.split(",");
        for (String id : idSplit) {
            if (!sum.has(id)) continue;
            org.json.JSONObject it = sum.getJSONObject(id);

            String title = it.optString("title");
            String link = "https://pubmed.ncbi.nlm.nih.gov/" + id + "/";
            Instant published = parsePub(it.optString("pubdate"));

            RecoItemDto dto = new RecoItemDto();
            dto.setType("article");
            dto.setTitle(title);
            dto.setUrl(link);
            dto.setSource("pubmed.ncbi.nlm.nih.gov");
            dto.setPublished(published);
            out.add(dto);
        }
        return out;
    }

    private Document fetchDoc(String url) throws Exception {
        return Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) " + "AppleWebKit/537.36 (KHTML, like Gecko) "
                        + "Chrome/124.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .referrer("https://www.google.com")
                .ignoreContentType(true)
                .timeout(12000)
                .followRedirects(true)
                .get();
    }

    /* ================== tiny utils ================== */

    private static int clampLimit(int limit) {
        return Math.min(30, Math.max(limit, 12));
    }

    private static String text(Element root, String css) {
        Element el = root.selectFirst(css);
        return (el != null) ? el.text() : "";
    }

    private static Instant parsePub(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        try {
            return Instant.parse(s);
        } catch (Exception ignore) {
        }
        try {
            ZonedDateTime zdt = ZonedDateTime.parse(s, DateTimeFormatter.RFC_1123_DATE_TIME);
            return zdt.toInstant();
        } catch (Exception ignore) {
        }
        try {
            String t = s.replaceAll("\\(.*?\\)", "").trim();
            ZonedDateTime zdt = ZonedDateTime.parse(t, DateTimeFormatter.RFC_1123_DATE_TIME);
            return zdt.toInstant();
        } catch (Exception ignore) {
        }
        return null;
    }

    private static String hostOf(String url) {
        try {
            URI u = new URI(url);
            String h = u.getHost();
            return (h != null) ? h : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }

    private static String firstImgFromHtml(String html) {
        if (html == null || html.isEmpty()) return null;
        Element img = org.jsoup.Jsoup.parse(html).selectFirst("img");
        if (img == null) return null;
        String src = img.absUrl("src");
        return (!src.isEmpty()) ? src : null;
    }

    private static boolean isBlank(String s) {
        return (s == null || s.trim().isEmpty());
    }

    private static String safeLower(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT);
    }

    private static String join(List<String> parts, String sep) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) sb.append(sep);
            sb.append(parts.get(i));
        }
        return sb.toString();
    }

    private static int comparePublishedDesc(Instant a, Instant b) {
        if (a == null && b == null) return 0;
        if (a == null) return 1;
        if (b == null) return -1;
        return b.compareTo(a);
    }

    /** Domain uy tín (nhẹ) */
    private static double domainBoost(String host) {
        if (host == null) return 0;
        String h = host.toLowerCase(Locale.ROOT);
        if (h.contains("vnexpress")) return 2.0;
        if (h.contains("tuoitre")) return 1.6;
        if (h.contains("znews") || h.contains("zing")) return 1.2;
        if (h.contains("vtc")) return 1.0;
        if (h.contains("phunuvietnam")) return 0.8;
        if (h.contains("pubmed")) return 0.5; // nghiên cứu (không "đè" tin thời sự)
        if (h.contains("google")) return 0.5; // news.google.com
        return 0.0;
    }

    /** Độ mới ưu tiên cao (0..3) */
    private static double recencyBoost(Instant published) {
        if (published == null) return 0;
        long days = Math.max(
                0, (java.time.Duration.between(published, Instant.now()).toDays()));
        if (days <= 7) return 3.0;
        if (days <= 30) return 2.0;
        if (days <= 90) return 1.0;
        return 0.3;
    }

    /** Đếm số lần xuất hiện đơn giản */
    private static int relevanceScore(String haystack, List<String> keywords) {
        if (haystack == null || haystack.isEmpty() || keywords == null || keywords.isEmpty()) return 0;
        String s = haystack.toLowerCase(Locale.ROOT);
        int score = 0;
        for (String k : keywords) {
            if (k == null || k.isEmpty()) continue;
            int idx = 0;
            String needle = k.toLowerCase(Locale.ROOT);
            while (true) {
                idx = s.indexOf(needle, idx);
                if (idx < 0) break;
                score++;
                idx += needle.length();
            }
        }
        return score;
    }

    /* ======= English query cho PubMed ======= */
    private static String viToEn(String s) {
        if (s == null) return null;
        String t = s.trim().toLowerCase(Locale.ROOT);
        return switch (t) {
            case "giảm cân" -> "weight loss";
            case "tăng cân" -> "weight gain";
            case "tăng cơ" -> "muscle gain";
            case "giữ cân" -> "maintenance";
            case "dinh dưỡng" -> "nutrition";
            case "thực đơn" -> "meal plan";
            case "dị ứng" -> "allergy";
            case "bệnh nền" -> "chronic disease";
            case "tiểu đường", "đái tháo đường" -> "diabetes";
            case "cao huyết áp", "tăng huyết áp" -> "hypertension";
            case "rối loạn mỡ máu" -> "dyslipidemia";
            case "béo phì" -> "obesity";
            case "tim mạch" -> "cardiovascular";
            default -> null;
        };
    }

    private static String buildEnglishQuery(ProfileDto p) {
        List<String> parts = new ArrayList<>();
        if (p != null) {
            if (!isBlank(p.getGoal())) {
                String en = viToEn(p.getGoal());
                if (en != null) parts.add(en);
            }
            if (p.getConditions() != null) {
                for (String c : p.getConditions()) {
                    String en = viToEn(c);
                    parts.add(en != null ? en : c);
                }
            }
            parts.addAll(Arrays.asList("nutrition", "diet", "meal plan", "health"));
        }
        return join(parts, " ");
    }

    /* ====== Nhận diện link/video/clip để loại bỏ ====== */
    private static boolean isVideoLike(String url, String source, String title) {
        String h = safeLower(hostOf(url));
        String s = safeLower(source);
        String t = safeLower(title);

        // Domain phổ biến của video
        if (h.contains("youtube.com")
                || h.contains("youtu.be")
                || h.contains("vimeo.com")
                || h.contains("dailymotion.com")
                || h.contains("facebook.com")
                || h.contains("fb.watch")
                || h.contains("tiktok.com")) return true;

        // title/source ám chỉ clip
        if (t.contains("video")
                || t.contains("clip")
                || t.contains("livestream")
                || t.contains("trực tiếp")
                || t.contains("phát trực tiếp")) return true;

        return s.contains("youtube") || s.contains("tiktok") || s.contains("facebook");
    }

    /* ========= Misc helpers ========= */

    private static String keyOf(RecoItemDto it) {
        return (safeLower(it.getUrl()) + "|" + safeLower(it.getTitle())).trim();
    }

    private static boolean containsKey(List<RecoItemDto> list, String key) {
        for (RecoItemDto it : list) {
            if (keyOf(it).equals(key)) return true;
        }
        return false;
    }

    /** Dedupe theo (url|title), giữ bài mới hơn nếu trùng key */
    private static List<RecoItemDto> dedupeArticles(List<RecoItemDto> list) {
        if (list == null || list.isEmpty()) return Collections.emptyList();
        Map<String, RecoItemDto> byKey = new LinkedHashMap<>();
        for (RecoItemDto it : list) {
            String key = keyOf(it);
            RecoItemDto old = byKey.get(key);
            if (old == null) {
                byKey.put(key, it);
            } else {
                // so published để giữ bài mới hơn
                Instant a = it.getPublished();
                Instant b = old.getPublished();
                int cmp = comparePublishedDesc(a, b); // b - a (desc)
                if (cmp >= 0) {
                    byKey.put(key, it); // it mới hơn (hoặc bằng) -> thay
                }
            }
        }
        return new ArrayList<>(byKey.values());
    }

    /** Tính tuổi từ birthYear (null-safe) */
    private Integer calcAge(Integer birthYear) {
        if (birthYear == null) return null;
        int now = Year.now().getValue();
        int age = now - birthYear;
        return (age >= 0) ? age : null;
    }

    /** Map GoalType -> tiếng Việt gọn */
    private String mapGoalToString(com.hn.nutricarebe.enums.GoalType goal) {
        if (goal == null) return null;
        return switch (goal) {
            case LOSE -> "giảm cân";
            case GAIN -> "tăng cân";
            case MAINTAIN -> "giữ cân";
        };
    }

    /** Map ActivityLevel -> tiếng Việt gọn */
    private String mapActivityToString(com.hn.nutricarebe.enums.ActivityLevel act) {
        if (act == null) return null;
        return switch (act) {
            case SEDENTARY -> "Ít vận động";
            case LIGHTLY_ACTIVE -> "Vận động nhẹ";
            case MODERATELY_ACTIVE -> "Vận động vừa phải";
            case VERY_ACTIVE -> "Vận động nhiều";
            case EXTRA_ACTIVE -> "Vận động rất nhiều";
        };
    }
}
