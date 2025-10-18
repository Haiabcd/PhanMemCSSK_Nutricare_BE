package com.hn.nutricarebe.service.impl;

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
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

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

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RecommendationServiceImpl implements RecommendationService {
    ProfileRepository profileRepository;
    UserAllergyRepository userAllergyRepository;
    UserConditionRepository userConditionRepository;

    /* ====== Nguồn uy tín (FREE) ====== */
    private static final List<String> ARTICLE_FEEDS = Arrays.asList(
            // 🇻🇳 Việt Nam
            "https://vnexpress.net/rss/mon-ngon.rss",
            "https://vnexpress.net/rss/suc-khoe/van-dong.rss",
            "https://vnexpress.net/rss/suc-khoe/dinh-duong.rss",
            "https://tuoitre.vn/rss/dinh-duong.rss",
            "https://phunuvietnam.vn/rss/dinh-duong.rss",
            "https://kenh14.vn/rss.chn",
            "https://vtc.vn/rss/song-khoe.rss",
            "https://znews.vn/rss/suc-khoe.rss",
            "https://tuoitre.vn/rss/suc-khoe.rss"
            /*
            // 🌍 Quốc tế
            "https://www.health.harvard.edu/blog/category/nutrition/feed",
            "https://newsnetwork.mayoclinic.org/category/nutrition/feed/",
            "https://www.nutrition.gov/rss.xml",
            "https://newsinhealth.nih.gov/rss",
            "https://www.menshealth.com/fitness/rss"

                */
    );

    private static String youtubeSearchRss(String query) {
        // chuẩn hoá: gộp khoảng trắng, cắt 120 ký tự, encode 1 lần
        String q = Optional.ofNullable(query).orElse("")
                .trim().replaceAll("\\s+", " ");
        if (q.length() > 120) q = q.substring(0, 120);
        String enc = URLEncoder.encode(q, StandardCharsets.UTF_8);
        return "https://www.youtube.com/feeds/videos.xml?channel_id=UC07qJE1XtXyUo8SSfZPe7Tg" + enc;
    }


    private static String pubmedSearchUrl(String query) {
        return "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi" +
                "?db=pubmed&retmode=json&retmax=10&term=" +
                URLEncoder.encode(query, StandardCharsets.UTF_8);
    }

    @Override
    public List<RecoItemDto> find(int limit) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated())
            throw new AppException(ErrorCode.UNAUTHORIZED);
        UUID userId = UUID.fromString(auth.getName());

        Optional<Profile> p = profileRepository.findByUser_Id(userId);

        List<UserAllergy> la =  userAllergyRepository.findByUser_Id(userId);
        List<UserCondition> lc =  userConditionRepository.findByUser_Id(userId);

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
                        .heightCm(pr.getHeightCm() == null ? null : pr.getHeightCm().doubleValue())
                        .weightKg(pr.getWeightKg() == null ? null : pr.getWeightKg().doubleValue())
                        .conditions(conditionNames)
                        .allergies(allergyNames)
                        .locale("vi")
                        .build())
                .orElseThrow(() -> new IllegalArgumentException("Profile not found for userId=" + userId));

        int lim = clampLimit(limit);

        List<RecoItemDto> items = new ArrayList<RecoItemDto>();
        String q = buildQuery(profile);
        List<String> keywords = buildKeywords(profile); // đã lower-case & lọc rỗng

        /*
        // 1) RSS bài viết
        for (String rss : ARTICLE_FEEDS) {
            try {
                items.addAll(fetchRssArticles(rss));
            } catch (Exception ignore) {}
        }

        // 2) YouTube (RSS)
        try { items.addAll(fetchYoutubeVideos(q)); } catch (Exception ignore) {}

        // 3) PubMed
        try { items.addAll(fetchPubMed(q)); } catch (Exception ignore) {}

         */

        for (String rss : ARTICLE_FEEDS) {
            try {
                List<RecoItemDto> a = fetchRssArticles(rss);
                System.out.println("[RSS OK] " + rss + " -> " + a.size());
                items.addAll(a);
            } catch (Exception e) {
                System.err.println("[RSS ERR] " + rss + " -> " + e.getMessage());
            }
        }
//        try {
//            List<RecoItemDto> y = fetchYoutubeVideos(q);
//            System.out.println("[YT OK] -> " + y.size());
//            items.addAll(y);
//        } catch (Exception e) {
//            System.err.println("[YT ERR] " + e.getMessage());
//        }
        try {
            List<RecoItemDto> pm = fetchPubMed(q);
            System.out.println("[PM OK] -> " + pm.size());
            items.addAll(pm);
        } catch (Exception e) {
            System.err.println("[PM ERR] " + e.getMessage());
        }
        System.out.println("[TOTAL ITEMS BEFORE FILTER] " + items.size());


        // 4) Lọc theo keywords + chấm điểm (an toàn null)
        List<RecoItemDto> filtered = new ArrayList<>();
        boolean noKw = keywords.isEmpty();

// Record tạm cho dễ sort
        record Scored(RecoItemDto it, int score) {}

        List<Scored> scoredList = new ArrayList<>();
        for (RecoItemDto it : items) {
            String t = safeLower(it.getTitle());
            String s = safeLower(it.getSource());
            String hay = (t + " " + s).trim();

            int score = noKw ? 1 : relevanceScore(hay, keywords);
            if (score > 0) {
                scoredList.add(new Scored(it, score));
            }
        }

        int relaxThreshold = Math.max(3, lim / 2);
        if (!noKw && filtered.size() < relaxThreshold) {
            System.out.println("[FILTER] too few matches (" + filtered.size() + "), relax filter -> return unfiltered");
            filtered = new ArrayList<>(items); // bỏ lọc từ khóa để đảm bảo có dữ liệu
        }

        // 5) Sort theo thời gian (mới nhất trước), null published cho xuống cuối
        filtered.sort(new java.util.Comparator<RecoItemDto>() {
            @Override
            public int compare(RecoItemDto a, RecoItemDto b) {
                Instant pa = a.getPublished();
                Instant pb = b.getPublished();
                if (pa == null && pb == null) return 0;
                if (pa == null) return 1;   // a xuống dưới
                if (pb == null) return -1;  // b xuống dưới
                // desc
                return pb.compareTo(pa);
            }
        });


        // 6) Cắt limit
        if (filtered.size() > lim) {
            return new ArrayList<RecoItemDto>(filtered.subList(0, lim));
        }
        return filtered;
    }

    /* ============================ Helpers ============================ */

    private Integer calcAge(Integer birthYear) {
        if (birthYear == null) return null;
        int nowYear = Year.now().getValue();
        int age = nowYear - birthYear;
        return age >= 0 ? age : null;
    }

    private String mapGoalToString(com.hn.nutricarebe.enums.GoalType goal) {
        if (goal == null) return null;
        switch (goal) {
            case LOSE:    return "giảm cân";
            case GAIN:    return "tăng cơ";
            case MAINTAIN:return "giữ cân";
            default: return goal.name().toLowerCase();
        }
    }

    private String mapActivityToString(com.hn.nutricarebe.enums.ActivityLevel act) {
        if (act == null) return null;
        switch (act) {
            case SEDENTARY: return "Ít vận động";
            case LIGHTLY_ACTIVE:     return "Vận động nhẹ";
            case MODERATELY_ACTIVE:  return "Vận động vừa phải";
            case VERY_ACTIVE:    return "Vận động nhiều";
            case EXTRA_ACTIVE: return "Vận động rất nhiều";
            default:        return act.name().toLowerCase();
        }
    }


    private List<RecoItemDto> fetchRssArticles(String rssUrl) throws Exception {
        Document doc = fetchDoc(rssUrl);
        List<RecoItemDto> out = new ArrayList<>();

        // Thử RSS trước
        List<Element> items = doc.select("item");
        boolean isAtom = false;
        if (items.isEmpty()) {
            items = doc.select("entry");
            isAtom = !items.isEmpty();
        }

        // Các từ khóa bắt buộc phải có để lọc (liên quan dinh dưỡng - thực phẩm)
        List<String> foodKeywords = Arrays.asList(
                "ăn", "món", "thực phẩm", "dinh dưỡng", "thực đơn",
                "ăn kiêng", "giảm cân", "tăng cân", "tăng cơ", "calo",
                "bữa sáng", "bữa trưa", "bữa tối", "chế độ ăn", "chất béo", "protein", "carb", "nước", "bổ sung"
        );

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

            if (link == null || link.trim().isEmpty()) continue;

            // Lọc bài không liên quan đến ăn uống
            String lowerTitle = title == null ? "" : title.toLowerCase(Locale.ROOT);
            boolean related = foodKeywords.stream().anyMatch(lowerTitle::contains);
            if (!related) continue; // bỏ qua bài không liên quan

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


    private List<RecoItemDto> fetchYoutubeVideos(String query) {
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private List<RecoItemDto> fetchPubMed(String query) throws Exception {
        // 1) esearch -> ids
        Document d1 = fetchDoc(pubmedSearchUrl(query));
        org.json.JSONObject search = new org.json.JSONObject(d1.text());
        org.json.JSONObject esr = search.optJSONObject("esearchresult");
        if (esr == null) return Collections.emptyList();
        org.json.JSONArray idList = esr.optJSONArray("idlist");
        if (idList == null || idList.length() == 0) return Collections.emptyList();

        List<String> idsArr = new ArrayList<String>();
        for (int i = 0; i < idList.length(); i++) {
            idsArr.add(String.valueOf(idList.get(i)));
        }
        String ids = join(idsArr, ",");

        // 2) esummary theo ids
        String sumUrl = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esummary.fcgi" +
                "?db=pubmed&retmode=json&id=" + ids;
        Document d2 = fetchDoc(sumUrl);
        org.json.JSONObject root = new org.json.JSONObject(d2.text());
        org.json.JSONObject sum = root.optJSONObject("result");
        if (sum == null) return Collections.emptyList();

        List<RecoItemDto> out = new ArrayList<RecoItemDto>();
        String[] idSplit = ids.split(",");
        for (int i = 0; i < idSplit.length; i++) {
            String id = idSplit[i];
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

    /* ===== Doc fetch (User-Agent trình duyệt, tránh 403) ===== */
    private Document fetchDoc(String url) throws Exception {
        return Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
                        "AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "Chrome/124.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .referrer("https://www.google.com")
                .ignoreContentType(true)
                .timeout(12000)
                .followRedirects(true)
                .get();
    }

    /* ================== tiny utils (Java 8 friendly) ================== */

    private static int clampLimit(int limit) {
        if (limit < 1) return 12;
        if (limit > 30) return 30;
        return limit;
    }

    private static String text(Element root, String css) {
        Element el = root.selectFirst(css);
        return (el != null) ? el.text() : "";
    }

    private static Instant parsePub(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        try { return Instant.parse(s); } catch (Exception ignore) {}
        try {
            ZonedDateTime zdt = ZonedDateTime.parse(s, DateTimeFormatter.RFC_1123_DATE_TIME);
            return zdt.toInstant();
        } catch (Exception ignore) {}
        try {
            String t = s.replaceAll("\\(.*?\\)", "").trim();
            ZonedDateTime zdt = ZonedDateTime.parse(t, DateTimeFormatter.RFC_1123_DATE_TIME);
            return zdt.toInstant();
        } catch (Exception ignore) {}
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
        Element img = Jsoup.parse(html).selectFirst("img");
        if (img == null) return null;
        String src = img.absUrl("src");
        return (src != null && !src.isEmpty()) ? src : null;
    }

    private static String buildQuery(ProfileDto p) {
        List<String> parts = new ArrayList<String>();
        if (p != null) {
            if (!isBlank(p.getGoal())) parts.add(p.getGoal());
            if (p.getConditions() != null)
                for (String c : p.getConditions()) if (!isBlank(c)) parts.add(c);
            if (p.getAllergies() != null)
                for (String a : p.getAllergies()) if (!isBlank(a)) parts.add(a);

            if ("vi".equalsIgnoreCase(p.getLocale())) {
                parts.add("dinh dưỡng");
                parts.add("thực đơn");
                parts.add("giảm cân");
                parts.add("dị ứng");
                parts.add("bệnh nền");
            } else {
                parts.add("nutrition");
                parts.add("diet");
                parts.add("meal plan");
            }
        }
        return join(parts, " ");
    }

    private static List<String> buildKeywords(ProfileDto p) {
        List<String> ks = new ArrayList<>();

        // ==== Từ khóa nền luôn có (VI) ====
        String[] baseVi = new String[]{
                "dinh dưỡng","thực phẩm","món ăn","công thức","nấu ăn",
                "thực đơn","ăn kiêng","giảm cân","tăng cơ",
                "tập luyện","bài tập","thể dục","vận động","calo","protein","carb","chất béo"
        };
        // ==== Bản EN cơ bản ====
        String[] baseEn = new String[]{
                "nutrition","food","recipe","diet","meal plan","healthy",
                "weight loss","muscle gain","workout","exercise","training","calorie","protein","carb","fat"
        };
        ks.addAll(Arrays.asList(baseVi));
        ks.addAll(Arrays.asList(baseEn));

        if (p != null) {
            // goal -> thêm từ khóa đặc thù
            if (!isBlank(p.getGoal())) {
                addWithEnglish(ks, p.getGoal());
                String g = p.getGoal().toLowerCase(Locale.ROOT);
                if (g.contains("giảm")) {
                    Collections.addAll(ks, "đốt mỡ","low calorie","calo thấp","ăn kiêng lành mạnh","giảm mỡ","cardio");
                } else if (g.contains("tăng cơ") || g.contains("tăng")) {
                    Collections.addAll(ks, "high protein","protein cao","tăng cân lành mạnh","luyện tập sức mạnh","strength training");
                } else if (g.contains("giữ cân")) {
                    Collections.addAll(ks, "cân bằng","balanced diet","duy trì");
                }
            }

            // conditions
            if (p.getConditions() != null) {
                for (String c : p.getConditions()) {
                    addWithEnglish(ks, c);
                    String lc = c == null ? "" : c.toLowerCase(Locale.ROOT);
                    if (lc.contains("tiểu đường") || lc.contains("đái tháo đường") || lc.contains("diabetes")) {
                        Collections.addAll(ks, "đường huyết","glycemic","low glycemic","ít đường");
                    } else if (lc.contains("huyết áp") || lc.contains("hypertension")) {
                        Collections.addAll(ks, "ít muối","huyết áp","tim mạch","heart healthy");
                    } else if (lc.contains("mỡ máu") || lc.contains("cholesterol") || lc.contains("dyslipidemia")) {
                        Collections.addAll(ks, "ít chất béo bão hòa","hdl","ldl","triglyceride");
                    }
                }
            }

            // allergies
            if (p.getAllergies() != null) {
                for (String a : p.getAllergies()) {
                    addWithEnglish(ks, a);
                    // VD: dị ứng sữa, trứng -> bài viết hay có từ này
                }
            }

            // ưu tiên VI nếu locale 'vi'
            if (!"vi".equalsIgnoreCase(p.getLocale())) {
                // nếu không phải vi, đảm bảo có EN cơ bản
                for (String s : baseEn) if (!ks.contains(s)) ks.add(s);
            }
        }

        // distinct, normalize
        List<String> uniq = new ArrayList<>();
        for (String k : ks) {
            if (k != null) {
                String t = k.trim().toLowerCase(Locale.ROOT);
                if (!t.isEmpty() && !uniq.contains(t)) uniq.add(t);
            }
        }
        return uniq;
    }

    /* ==== relevance scoring (điểm mức liên quan) ==== */
    private static int relevanceScore(String haystack, List<String> keywords) {
        if (haystack == null || haystack.isEmpty() || keywords == null || keywords.isEmpty()) return 0;
        String s = haystack.toLowerCase(Locale.ROOT);
        int score = 0;
        for (String k : keywords) {
            if (k == null || k.isEmpty()) continue;
            // đếm số lần xuất hiện đơn giản
            int idx = 0;
            while (true) {
                idx = s.indexOf(k, idx);
                if (idx < 0) break;
                score++;
                idx += k.length();
            }
        }
        return score;
    }



    private static String safeLower(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT);
    }

    private static boolean isBlank(String s) {
        return (s == null || s.trim().isEmpty());
    }

    private static String join(List<String> parts, String sep) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) sb.append(sep);
            sb.append(parts.get(i));
        }
        return sb.toString();
    }

    /** map vi -> en đơn giản cho goal/keywords phổ biến */
    private static String viToEn(String s) {
        if (s == null) return null;
        String t = s.trim().toLowerCase(Locale.ROOT);
        switch (t) {
            case "giảm cân": return "weight loss";
            case "tăng cân": return "weight gain";
            case "tăng cơ":  return "muscle gain";
            case "giữ cân":  return "maintenance";
            case "dinh dưỡng": return "nutrition";
            case "thực đơn": return "meal plan";
            case "dị ứng":   return "allergy";
            case "bệnh nền": return "chronic disease";
            // một số bệnh thường gặp (tuỳ DB của bạn, bổ sung thêm nếu cần):
            case "tiểu đường":
            case "đái tháo đường": return "diabetes";
            case "cao huyết áp":
            case "tăng huyết áp":   return "hypertension";
            case "rối loạn mỡ máu": return "dyslipidemia";
            case "béo phì":         return "obesity";
            case "tim mạch":        return "cardiovascular";
            default: return null; // không dịch được thì trả null
        }
    }

    /** Thêm bản tiếng Anh của từ khóa (nếu có) vào danh sách */
    private static void addWithEnglish(List<String> bag, String vi) {
        if (isBlank(vi)) return;
        String viNorm = vi.toLowerCase(Locale.ROOT).trim();
        if (!bag.contains(viNorm)) bag.add(viNorm);
        String en = viToEn(vi);
        if (en != null && !bag.contains(en)) bag.add(en);
    }

    /** tạo chuỗi tìm kiếm tiếng Anh cho PubMed */
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
            if (p.getAllergies() != null) {
                for (String a : p.getAllergies()) {
                    String en = viToEn(a);
                    parts.add(en != null ? en : a);
                }
            }
            // thêm các từ chung bằng tiếng Anh để tăng recall
            parts.add("nutrition");
            parts.add("diet");
            parts.add("meal plan");
        }
        return join(parts, " ");
    }

}
