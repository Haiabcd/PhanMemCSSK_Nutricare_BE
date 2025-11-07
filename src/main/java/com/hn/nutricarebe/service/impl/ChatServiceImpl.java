package com.hn.nutricarebe.service.impl;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;

import com.google.genai.errors.ClientException;
import com.hn.nutricarebe.dto.ai.*;
import com.hn.nutricarebe.entity.Nutrition;
import com.hn.nutricarebe.enums.RuleScope;
import com.hn.nutricarebe.enums.TargetType;
import com.hn.nutricarebe.exception.AppException;
import com.hn.nutricarebe.exception.ErrorCode;
import com.hn.nutricarebe.service.ChatService;
import com.hn.nutricarebe.service.NutritionRuleService;
import com.hn.nutricarebe.service.TagService;
import com.hn.nutricarebe.service.tools.MealPlanTool;
import com.hn.nutricarebe.service.tools.ProfileTool;

@Service
public class ChatServiceImpl implements ChatService {
    private final ChatClient chatClient;
    private final TagService tagService;
    private final NutritionRuleService nutritionRuleService;

    // === SYSTEM PROMPT NGẮN + HƯỚNG DẪN GỌI TOOL ===
    private static final String NUTRICARE_SYSTEM_PROMPT =
            """
Bạn là NutriCare Assistant – trợ lý dinh dưỡng trong hệ thống NutriCare.

PHONG CÁCH & MỤC TIÊU
- Giọng điệu: THÂN THIỆN, TÔN TRỌNG, CHUYÊN MÔN, ngắn gọn – dễ hiểu – hữu ích.
- Ưu tiên sự hài lòng người dùng: đề xuất rõ ràng, dễ hành động, có tùy chọn thay thế.
- Khi THIẾU hoặc MƠ HỒ dữ liệu (ví dụ: mục tiêu, dị ứng, sở thích, giới hạn thời gian nấu, ngân sách…):
→ HỎI LẠI TỐI ĐA 1–2 CÂU NGẮN, rồi tiếp tục xử lý.
- Khi sinh kế hoạch hoặc đề xuất quan trọng:
→ TÓM TẮT NGẮN (≤5 câu) + HỎI XÁC NHẬN “Bạn muốn giữ như thế hay đổi món X/Y?”.
- Luôn lịch sự mời người dùng góp ý để cải thiện ở vòng sau.

QUY TẮC BẮT BUỘC (DỮ LIỆU & TOOL)
- KHÔNG bịa dữ liệu. Chỉ dùng profile/rules/foods từ tool.
- Đơn vị bắt buộc: kcal, proteinG, carbG, fatG, fiberG, sodiumMg, sugarMg.
- Khi cần hồ sơ/demographics/targets/rules/foods:
1) Gọi và THỰC THI:
	- getProfileSummary() để lấy hồ sơ tóm tắt, hoặc
	- createMealPlanningContext(days=?, overrides=?, foodsLimit=40, foodsCursor="0", slot=?, keyword=?)
	→ nhận: effectiveProfile, dailyTargets (kèm water), rules, foods (trang đầu), slotKcalPct, slotItemCounts.
2) Nếu danh sách món chưa đủ, gọi tiếp:
	- getFoodsPage(limit=40, cursor=nextCursor, slot=?, keyword=?)
2a) Khi đã biết slotKcal và avg kcal per item:
	- ƯU TIÊN gọi getFoodsCandidatesByKcalWindow(slot, perItemTargetKcal, 0.5, 2.0, 80)
	để lấy pool ứng viên đúng “cửa sổ kcal” cho bữa đó.
- Hỏi BMI → calcBmi(overrideWeightKg?, overrideHeightCm?).
- Hỏi mục tiêu ngày/nước/macro → getDailyTargets(overrides?).

KẾ HOẠCH ĂN – ĐẦU RA XEM TRƯỚC (KHÔNG COMMIT DB)
- Trả JSON preview theo schema:
{
"days": <int>,
"plan": [
	{
	"date": "YYYY-MM-DD",
	"slots": {
		"BREAKFAST": [ {"foodId":"<UUID>","portion":1.0}, ... ],
		"LUNCH":     [ ... ],
		"DINNER":    [ ... ],
		"SNACK":     [ ... ]
	}
	}
],
"notes": "≤5 câu nhận xét/giải thích ngắn"
}
- portion ∈ {1.5, 1.0, 0.5}. Giữ SLOT_ITEM_COUNTS & tránh trùng món trong 3 ngày.
- Tôn trọng rules AVOID/LIMIT/PREFER. Không chọn món thiếu nutrition bắt buộc (kcal/proteinG/carbG/fatG) nếu cần tính macro.
- Sau khi trả JSON preview: HỎI XÁC NHẬN (giữ/đổi món/đổi slot/tăng-giảm khẩu phần).

GIẢI THÍCH & TƯ VẤN
- Khi người dùng yêu cầu giải thích: mô tả ngắn gọn cách tính (TDEE, target kcal, phân bổ theo slot), nêu các ràng buộc rule quan trọng (ví dụ hạn chế sodium/sugar), KHÔNG bịa số liệu thiếu.
- Khi người dùng muốn ĐỔI MÓN: tính perItemTargetKcal cho slot, gọi getFoodsCandidatesByKcalWindow để đề xuất 2–3 lựa chọn thay thế phù hợp macro/rules, kèm khẩu phần {1.5,1.0,0.5}.

NGOÀI PHẠM VI & AN TOÀN
- Câu hỏi không liên quan đến dinh dưỡng/kế hoạch ăn/foods/profile/rules của hệ thống:
→ Lịch sự từ chối: “Mình đang hỗ trợ dinh dưỡng trong NutriCare nên không thể tư vấn chủ đề này.”
- Nội dung y khoa (chẩn đoán bệnh, điều trị, thuốc) vượt quá dữ liệu hồ sơ & rule:
→ Đưa khuyến cáo chung, TRÁNH kết luận y khoa, khuyên gặp bác sĩ/chuyên gia dinh dưỡng.
- Nếu thiếu dữ liệu trọng yếu để đảm bảo an toàn (dị ứng/bệnh nền…):
→ HỎI LẠI NGẮN GỌN trước khi đề xuất.
""";

    private static final String DISH_COPYWRITER_SYSTEM_PROMPT =
            """
Bạn là copywriter ẩm thực của NutriCare. Viết mô tả món ăn NGẮN GỌN, THÂN THIỆN, dễ hiểu cho người Việt.
QUY TẮC:
- Luôn viết bằng TIẾNG VIỆT, giọng điệu thân thiện, gợi cảm giác ngon miệng; ưu tiên từ vựng đời thường.
- Nếu có ẢNH: hãy QUAN SÁT màu sắc, độ bóng, texture, bố cục trình bày để đưa 1–2 chi tiết thị giác vào mô tả.
- Độ dài: 5–7 câu; KHÔNG gạch đầu dòng, KHÔNG chèn mã/ký hiệu lạ.
- Nhấn 1–2 “điểm ăn tiền”: hương vị/texture/chế biến/điểm phục vụ (nóng, lạnh), gợi ý ăn kèm tự nhiên.
- Không phóng đại công dụng y khoa; không bịa số dinh dưỡng.
- Nếu có dinh dưỡng (kcal, proteinG, carbG, fatG, fiberG, sodiumMg, sugarMg), chỉ nhắc NHẸ 1 câu ở cuối (không quá kỹ thuật).
- Kết đoạn bằng 1 hook ngắn gợi dùng ngay (tự nhiên, lịch sự).
Nếu trường nào thiếu thì bỏ qua, không suy diễn.
""";
    private static final String ADD_RULE_SYSTEM_PROMPT =
            """
Bạn là chuyên gia dinh dưỡng kiêm kỹ sư backend.
NHIỆM VỤ: sinh mảng JSON NutritionRuleAI.
YÊU CẦU: - Chỉ trả JSON hợp lệ, field trùng tên DTO.
RÀNG BUỘC:
1) Bắt buộc: ruleType, scope, targetType, message (≤1000).
2) targetType=NUTRIENT:
• BETWEEN: thresholdMin ≤ thresholdMax
• LT/LTE: chỉ thresholdMax
• GT/GTE: chỉ thresholdMin
• EQ: min=max
• perKg: true/false; foodTags & customFoodTags = []
3) targetType=FOOD_TAG:
• Ưu tiên dùng đúng nameCode hiện có (đưa vào foodTags).
• Nếu thiếu, tạo Tag mới vào customFoodTags: { nameCode, description≤120 ký tự }.
• comparator/threshold/perKg = null; scope mặc định ITEM nếu không nêu.
• Cần ≥1 tag trong foodTags hoặc customFoodTags.
4) frequencyPerScope: null hoặc số nguyên dương.
5) ageMin≤ageMax nếu cả hai có.
6) Nếu nhiều rule chỉ khác về tag/message → gộp (union tag, message đại diện).
""";
    private static final String VISION_SYSTEM_PROMPT =
            """
Bạn là chuyên gia ẩm thực NutriCare, PHÂN TÍCH ẢNH MÓN ĂN VIỆT NAM.
YÊU CẦU ĐẦU RA (BẮT BUỘC):
- TRẢ LỜI 100% BẰNG TIẾNG VIỆT.
- Chỉ trả về JSON hợp lệ theo DTO DishVisionResult (không kèm giải thích, không markdown).
- Phải có: dishName (tên món, tiếng Việt, ≤60 ký tự; nếu không chắc thì ước đoán gần nhất).
- Dinh dưỡng là CHO 1 KHẨU PHẦN; đủ: kcal, proteinG, carbG, fatG, fiberG, sodiumMg, sugarMg.
- servingName ngắn gọn (“phần”, “bát”, “đĩa”, “ly”…); servingGram nếu ước tính được (gram).
- ingredients: 5–12 nguyên liệu TÊN TIẾNG VIỆT; mỗi item { name, amount (số), unit (GRAM|ML) }.
- Nếu không chắc, vẫn phải ước tính hợp lý theo nguồn uy tín của Việt Nam; KHÔNG để null các trường dinh dưỡng.
""";

    public ChatServiceImpl(
            ChatClient.Builder builder,
            JdbcChatMemoryRepository jdbcChatMemoryRepository,
            ProfileTool profileTool,
            TagService tagService,
            NutritionRuleService nutritionRuleService,
            MealPlanTool mealPlanTool) {
        this.tagService = tagService;
        this.nutritionRuleService = nutritionRuleService;

        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(jdbcChatMemoryRepository)
                .maxMessages(15)
                .build();

        chatClient = builder.defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultTools(profileTool, mealPlanTool) // Đăng ký tool
                .build();
    }

    @Override
    public String chat(MultipartFile file, String message) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        UUID userId = UUID.fromString(auth.getName());

        String conversationId = userId.toString();

        ChatOptions chatOptions = ChatOptions.builder().temperature(0D).build();

        var prompt = chatClient
                .prompt()
                .options(chatOptions)
                .system(NUTRICARE_SYSTEM_PROMPT)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId));

        String safeMsg =
                (message != null && !message.isBlank()) ? message : "Phân tích ảnh/trao đổi theo hướng dẫn hệ thống.";

        if (file != null && !file.isEmpty() && file.getContentType() != null) {
            var media = Media.builder()
                    .mimeType(MimeTypeUtils.parseMimeType(file.getContentType()))
                    .data(file.getResource())
                    .build();
            prompt.user(u -> u.media(media).text(safeMsg));
        } else {
            prompt.user(safeMsg);
        }
        return prompt.call().content();
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public String writeDishDescription(SuggestionAI request) {

        String conversationId = "ADMIN";

        ChatOptions opts = ChatOptions.builder().temperature(0.7D).build();

        var prompt = chatClient
                .prompt()
                .options(opts)
                .system(DISH_COPYWRITER_SYSTEM_PROMPT)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId));

        // Xây user message: đưa tên món + mục tiêu + giới hạn độ dài + dinh dưỡng
        StringBuilder userMsg = new StringBuilder();
        userMsg.append("Yêu cầu: Viết mô tả món ăn ngắn gọn, thân thiện, tăng cảm giác ngon miệng.\n");
        userMsg.append("Tên món: ")
                .append(request.getDishName() == null ? "Không rõ" : request.getDishName())
                .append("\n");
        if (request.getNutrition() != null) {
            Nutrition nutrition = request.getNutrition();
            userMsg.append("Dinh dưỡng (nếu thấy phù hợp chỉ nên nhắc nhẹ 1 câu):\n")
                    .append("- kcal: ")
                    .append(nutrition.getKcal())
                    .append("\n")
                    .append("- proteinG: ")
                    .append(nutrition.getProteinG())
                    .append("\n")
                    .append("- carbG: ")
                    .append(nutrition.getCarbG())
                    .append("\n")
                    .append("- fatG: ")
                    .append(nutrition.getFatG())
                    .append("\n")
                    .append("- fiberG: ")
                    .append(nutrition.getFiberG())
                    .append("\n");
        }
        userMsg.append("\nĐầu ra mong muốn: 1 đoạn mô tả 5–7 câu, không gạch đầu dòng, không chèn mã hoặc ký hiệu lạ.");
        userMsg.append("Ảnh đính kèm chính là món cần mô tả; hãy quan sát để đưa chi tiết thị giác vào 1–2 câu.\n");

        MultipartFile image = request.getImage();
        if (image != null && !image.isEmpty() && image.getContentType() != null) {
            var media = Media.builder()
                    .mimeType(MimeTypeUtils.parseMimeType(image.getContentType()))
                    .data(image.getResource())
                    .build();
            prompt.user(u -> u.media(media).text(userMsg.toString()));
        } else {
            prompt.user(userMsg.toString());
        }
        return prompt.call().content();
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public void addRule(CreationRuleAI request) {
        ChatOptions chatOptions = ChatOptions.builder().temperature(0D).build();
        // Lấy từ điển đã cache sẵn
        String tagVocabulary = tagService.getTagVocabularyText();

        SystemMessage systemMessage = new SystemMessage(ADD_RULE_SYSTEM_PROMPT + "\n" + tagVocabulary);

        UserMessage userMessage = new UserMessage(request.getMessage());
        Prompt prompt = new Prompt(systemMessage, userMessage);

        List<NutritionRuleAI> rules =
                chatClient.prompt(prompt).options(chatOptions).call().entity(new ParameterizedTypeReference<>() {});
        if (rules == null) rules = List.of();

        for (NutritionRuleAI r : rules) {
            normalizeFoodTags(r);
        }
        nutritionRuleService.saveRules(request, rules);
    }

    @Override
    public DishVisionResult analyzeDishFromImage(MultipartFile image, String hint) {
        if (image == null || image.isEmpty() || image.getContentType() == null) {
            throw new AppException(ErrorCode.VALIDATION_FAILED);
        }

        ChatOptions opts = ChatOptions.builder().temperature(0D).build();

        var media = Media.builder()
                .mimeType(MimeTypeUtils.parseMimeType(image.getContentType()))
                .data(image.getResource())
                .build();

        String userText = (hint != null && !hint.isBlank())
                ? "Gợi ý: " + hint + "\nHãy trả JSON DishVisionResult."
                : "Phân tích ảnh món ăn và trả JSON DishVisionResult.";
        try {
            DishVisionResult result = chatClient
                    .prompt()
                    .options(opts)
                    .system(VISION_SYSTEM_PROMPT)
                    .user(u -> u.text(userText).media(media))
                    .call()
                    .entity(new ParameterizedTypeReference<>() {});
            ensureNutritionFields(result);
            ensureDishName(result, hint);
            return result;
        } catch (ClientException ex) {
            if (ex.getMessage() != null && ex.getMessage().contains("429")) {
                throw new AppException(ErrorCode.AI_SERVICE_ERROR);
            }
            throw ex;
        }
    }

    // ==========================HELPER========================================//
    private void ensureDishName(DishVisionResult r, String hint) {
        if (r == null) return;
        String name = (r.getDishName() == null) ? "" : r.getDishName().trim();
        if (!name.isEmpty()) return;
        if (hint != null && !hint.isBlank()) {
            r.setDishName(hint.trim());
            return;
        }
        // Fallback cuối
        r.setDishName("Món ăn không rõ");
    }

    private void normalizeFoodTags(NutritionRuleAI r) {
        if (r.getTargetType() != TargetType.FOOD_TAG) {
            if (r.getFoodTags() != null) r.getFoodTags().clear();
            if (r.getCustomFoodTags() != null) r.getCustomFoodTags().clear();
            return;
        }
        // Nếu là FOOD_TAG
        r.setScope(RuleScope.ITEM);
        r.setComparator(null);
        r.setThresholdMin(null);
        r.setThresholdMax(null);
        r.setPerKg(Boolean.FALSE);
        r.setTargetCode(null);

        // Gom tất cả input (từ 2 mảng) -> chuẩn hoá -> distinct
        Set<String> inputCodes = new HashSet<>();
        if (r.getFoodTags() != null) inputCodes.addAll(r.getFoodTags());
        Map<String, String> customMap = new HashMap<>();
        if (r.getCustomFoodTags() != null) {
            for (TagCreationRequest t : r.getCustomFoodTags()) {
                if (t == null) continue;
                String code = toNameCode(t.getNameCode());
                String desc = t.getDescription();
                customMap.putIfAbsent(code, desc);
                inputCodes.add(code);
            }
        }
        Set<String> normalized = inputCodes.stream()
                .map(this::toNameCode)
                .filter(s -> s != null && !s.isBlank())
                .collect(java.util.stream.Collectors.toSet());

        if (normalized.isEmpty()) {
            r.setFoodTags(Set.of());
            r.setCustomFoodTags(Set.of());
            return;
        }

        List<String> existing = tagService.findNameCodeByNormal(normalized);
        Set<String> known =
                new HashSet<>(existing.stream().map(this::toNameCode).toList());
        Set<TagCreationRequest> custom = normalized.stream()
                .filter(c -> !known.contains(c))
                .map(c -> TagCreationRequest.builder()
                        .nameCode(c)
                        .description(customMap.get(c))
                        .build())
                .collect(Collectors.toSet());
        r.setFoodTags(known);
        r.setCustomFoodTags(custom);
    }

    private String toNameCode(String s) {
        if (s == null) return null;
        return s.trim()
                .replaceAll("[^A-Za-z0-9]+", "_") // non-alnum -> underscore
                .replaceAll("_+", "_") // gộp _
                .replaceAll("^_|_$", "") // bỏ _ đầu/cuối
                .toLowerCase();
    }

    private void ensureNutritionFields(DishVisionResult r) {
        if (r == null) return;
        if (r.getNutrition() == null) {
            r.setNutrition(new Nutrition());
        }
        Nutrition n = r.getNutrition();
        if (n.getKcal() == null) n.setKcal(java.math.BigDecimal.ZERO);
        if (n.getProteinG() == null) n.setProteinG(java.math.BigDecimal.ZERO);
        if (n.getCarbG() == null) n.setCarbG(java.math.BigDecimal.ZERO);
        if (n.getFatG() == null) n.setFatG(java.math.BigDecimal.ZERO);
        if (n.getFiberG() == null) n.setFiberG(java.math.BigDecimal.ZERO);
        if (n.getSodiumMg() == null) n.setSodiumMg(java.math.BigDecimal.ZERO);
        if (n.getSugarMg() == null) n.setSugarMg(java.math.BigDecimal.ZERO);
    }
}
