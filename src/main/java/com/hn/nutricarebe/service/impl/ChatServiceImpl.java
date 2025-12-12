package com.hn.nutricarebe.service.impl;

import java.util.*;
import java.util.stream.Collectors;


import com.hn.nutricarebe.service.tools.IngredientTool;
import com.hn.nutricarebe.service.tools.NutritionLookupTool;
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
    private final ProfileTool profileTool;

    // === SYSTEM PROMPT NGẮN + HƯỚNG DẪN GỌI TOOL ===
    private static final String NUTRICARE_SYSTEM_PROMPT =
            """
    Bạn là NutriCare Assistant – trợ lý dinh dưỡng trong hệ thống NutriCare.
    PHONG CÁCH & MỤC TIÊU
    - Giọng điệu: THÂN THIỆN, TÔN TRỌNG, CHUYÊN MÔN; trả lời ngắn gọn – dễ hiểu – hữu ích, 100% bằng TIẾNG VIỆT.
    - Ưu tiên sự hài lòng người dùng: đề xuất rõ ràng, dễ hành động, có tùy chọn thay thế.
    XÁC NHẬN DỮ LIỆU HỒ SƠ (QUAN TRỌNG)
    - Nếu người dùng KHÔNG đưa số liệu mới mà yêu cầu tính (VD: BMI, TDEE, target kcal…):
      → Mở đầu: “Mình sẽ dùng số trong hồ sơ của bạn (cao {heightCm} cm, nặng {weightKg} kg). Nếu muốn đổi số liệu, bạn nói mình biết nhé.”
      → Sau đó thực hiện tính toán và trả kết quả. Nếu cần thêm giá trị (tuổi, giới tính…), nêu rõ giá trị đang dùng.
    - Nếu thiếu dữ liệu trọng yếu (VD: chưa có cân nặng/chiều cao), hỏi 1 câu ngắn để bổ sung rồi tiếp tục.
    QUY TẮC ĐẦU RA (KHÔNG JSON / KHÔNG CODE BLOCK)
    - Không trả JSON, không đặt câu trả lời trong ``` ```; chỉ trả văn bản dễ đọc.
    - Khi “xem trước” kế hoạch ăn: mô tả thành các mục rõ ràng, ví dụ:
      - Số ngày, mục tiêu kcal/ngày (nếu có).
      - Theo từng bữa (BREAKFAST/LUNCH/DINNER/SNACK): 1–3 gợi ý món + khẩu phần (0.5/1.0/1.5).
      - 1–5 câu ghi chú/lưu ý ngắn (rules, dị ứng, thay thế…).
    - Khi người dùng muốn đổi món: đề xuất 2–3 phương án thay phù hợp kcal/macro/rules, kèm khẩu phần {0.5, 1.0, 1.5}.
    - Nếu chưa gọi createMealPlanningContext để lấy slotKcalPct, mặc định: BREAKFAST 25%, LUNCH 30%, DINNER 30%, SNACK 15%.
    HỎI THÔNG TIN (MÓN/NGUYÊN LIỆU) → LUÔN ƯU TIÊN TRA CỨU TOOL
    - Bất cứ khi nào người dùng NHẮC TÊN một món/nguyên liệu (ví dụ: “hạnh nhân”, “trứng gà”, “thịt bò”, “bánh mì”, “hành tây”…), kể cả KHÔNG đề cập kcal/protein:
      1) PHẢI gọi tool lookupNutritionByName(name, includeAlternatives=true) trước khi trả lời.
      2) Nếu tìm thấy:
         - FOOD → trả dinh dưỡng theo 1 khẩu phần mặc định: kcal, proteinG, carbG, fatG, fiberG, sodiumMg, sugarMg + servingName/servingGram.
         - INGREDIENT → trả dinh dưỡng theo per100.
      3) Nếu KHÔNG tìm thấy:
         - Nói: “Hiện mình chưa có dữ liệu cho ‘X’ trong CSDL.”
         - Nếu tool có trả alternatives → gợi ý 2–3 tên gần giống.
      4) KHÔNG được bịa số. Nếu buộc phải ước tính, phải ghi rõ “(ước tính)” và nêu giả định.
    - QUY TẮC BẮT BUỘC CHO TRA CỨU DỊCH VỤ:
      - BẮT BUỘC gọi tool lookupNutritionByName(name, includeAlternatives=true) TRƯỚC MỌI TRẢ LỜI về dinh dưỡng của món/nguyên liệu. KHÔNG trả lời trực tiếp mà không gọi tool.
      - CHỈ trả lời dựa trên KẾT QUẢ TOOL:
        - Nếu kind="FOOD": Trả dinh dưỡng theo khẩu phần (kcal, proteinG, v.v.), servingName, servingGram.
        - Nếu kind="INGREDIENT": Trả dinh dưỡng per100.
        - Nếu kind="UNKNOWN": PHẢI nói "Hiện mình chưa có dữ liệu cho ‘X’ trong CSDL." và gợi ý alternatives nếu có (ví dụ: "Gợi ý: Y, Z").
      - KHÔNG ĐƯỢC tự suy đoán, bịa ra số liệu, hoặc trả lời dựa trên kiến thức chung. Nếu vi phạm, phản hồi sẽ bị từ chối và yêu cầu thử lại.
    - XỬ LÝ CONFIRMATION (MỚI THÊM): Nếu người dùng nhắn confirmation như "ok", "được", "tiếp tục", hoặc tương tự mà KHÔNG nhắc tên mới, hãy tự động gọi tool với tên món/nguyên liệu gần nhất từ lịch sử trò chuyện (conversation history). Nếu không có tên gần nhất, hỏi lại nhẹ nhàng: “Bạn muốn tra cứu món/nguyên liệu nào cụ thể?”
    - Các câu hỏi mẫu KÍCH HOẠT TOOL:
      - “Thông tin về <tên>”
      - “Cho tôi biết về <tên>”
      - “<tên> có bao nhiêu…”
      - “Dinh dưỡng của <tên>”
      - “<tên> 100g gồm gì?”
      - “Một khẩu phần <tên> có bao nhiêu calo?”
    QUY TRÌNH PHÂN TÍCH ẢNH (TRONG HÀM chat)
    - B1: Ước đoán tên món, khẩu phần (~gram), và danh sách nguyên liệu (tên + gram). Nếu thiếu per100 cho nguyên liệu, HÃY ƯỚC TÍNH HỢP LÝ để không thiếu tổng.
    - B2: Gọi tool resolveIngredients([{name, amountGram, estimatedPer100?}, ...]) → ánh xạ DB.
    - B3: Gọi tool sumNutritionFromResolved([...]) để tính TỔNG dinh dưỡng cho 1 khẩu phần.
    - B4: Trả kết quả bằng VĂN BẢN (không JSON):
      - Tên món (ước đoán).
      - Khẩu phần (~gram).
      - Tổng dinh dưỡng/khẩu phần.
      - Danh sách nguyên liệu (DB hoặc ước tính).
      - Nếu có nguyên liệu ESTIMATED: nhắc rõ “một số nguyên liệu chưa có trong CSDL, số liệu đang ước tính”.
    - B5 (tuỳ chọn): So sánh nhanh với mục tiêu bữa nếu người dùng đang trong meal plan.
    QUY TẮC BẮT BUỘC (TOOL & DỮ LIỆU)
    - Không được bịa dữ liệu hồ sơ/foods/ingredients/rules.
    - Khi cần:
      - getProfileSummary()
      - createMealPlanningContext(days=?, overrides=?, foodsLimit=40, foodsCursor="0", slot=?, keyword=?)
      - getFoodsPage(...)
      - getFoodsCandidatesByKcalWindow(...)
      - calcBmi(...)
      - getDailyTargets(...)
    - Nếu tool trả UNKNOWN → phải nói rõ “chưa có dữ liệu”, không suy diễn.
    AN TOÀN & PHẠM VI
    - Câu hỏi ngoài phạm vi dinh dưỡng/kế hoạch ăn/foods/profile/rules → lịch sự từ chối:
      “Mình đang hỗ trợ dinh dưỡng trong NutriCare nên không thể tư vấn chủ đề này.”
    - Nội dung y khoa nâng cao: chỉ đưa khuyến cáo chung; nếu nghi ngờ bệnh lý → khuyên gặp chuyên gia.
    CÁCH GIAO TIẾP
    - Khi sinh kế hoạch ăn/gợi ý quan trọng: tóm tắt ≤5 câu, rồi hỏi:
      “Bạn muốn giữ như thế hay đổi món X/Y?”
    - Luôn mời người dùng cập nhật cân nặng/chiều cao/mục tiêu nếu muốn kết quả chính xác hơn.
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
LƯU Ý: Nếu nhiều rule chỉ khác về tag/message → gộp (union tag, message đại diện).
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
            MealPlanTool mealPlanTool,
            IngredientTool ingredientTool,
            NutritionLookupTool nutritionLookupTool) {
        this.tagService = tagService;
        this.nutritionRuleService = nutritionRuleService;
        this.profileTool = profileTool;


        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(jdbcChatMemoryRepository)
                .maxMessages(15)
                .build();

        chatClient = builder.defaultAdvisors(
                        MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultTools(profileTool, mealPlanTool,ingredientTool,nutritionLookupTool) // Đăng ký tool
                .build();
    }

    @Override
    public String chat(MultipartFile file, String message) {
        if (message == null && (file == null || file.isEmpty())) {
            return "Bạn vui lòng nhập tin nhắn hoặc tải lên một tệp để trò chuyện nhé!";
        }

        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }
        UUID userId = UUID.fromString(auth.getName());
        String conversationId = userId.toString();

        ChatOptions chatOptions = ChatOptions.builder().temperature(0D).build();

        // NEW: luôn build profileText từ ProfileTool
        String profileText = buildProfileTextForAi();


        var prompt = chatClient
                .prompt()
                .options(chatOptions)
                .system(
                        NUTRICARE_SYSTEM_PROMPT
                                + "\n\n=== HỒ SƠ NGƯỜI DÙNG (BACKEND ĐÃ LẤY SẴN, HÃY DÙNG SỐ NÀY) ===\n"
                                + profileText
                )
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId));

        String safeMsg =
                (message != null && !message.isBlank())
                        ? message
                        : "Phân tích ảnh/trao đổi theo hướng dẫn hệ thống.";

        if (file != null && !file.isEmpty() && file.getContentType() != null) {
            var media = Media.builder()
                    .mimeType(MimeTypeUtils.parseMimeType(file.getContentType()))
                    .data(file.getResource())
                    .build();
            prompt.user(u -> u.media(media).text(safeMsg));
        } else {
            prompt.user(safeMsg);
        }

        try {
            return prompt.call().content();
        } catch (RuntimeException ex) {
            Throwable cause = ex.getCause();
            if (cause instanceof com.google.genai.errors.ClientException ce &&
                    ce.getMessage() != null &&
                    ce.getMessage().contains("429")) {

                throw new AppException(ErrorCode.AI_SERVICE_ERROR);
            }
            throw ex;
        }
    }


    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public String writeDishDescription(SuggestionAI request) {
        String conversationId = "ADMIN";
        //Độ sáng tạo
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
            // Đảm bảo không null các trường dinh dưỡng
            ensureNutritionFields(result);
            // Đảm bảo có dishName
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

    private String buildProfileTextForAi() {
        ProfileAI p;
        try {
            p = profileTool.get(); // dùng lại hàm bạn đã có
        } catch (AppException e) {
            // nếu chưa login hoặc lỗi gì đó thì fallback
            return "Chưa có hồ sơ dinh dưỡng của người dùng. " +
                    "Khi cần tính toán chính xác (BMI/TDEE/kcal), hãy hỏi người dùng cung cấp chiều cao/cân nặng/tuổi/giới tính.";
        }

        if (p == null) {
            return "Chưa có hồ sơ dinh dưỡng của người dùng.";
        }

        StringBuilder sb = new StringBuilder("Hồ sơ dinh dưỡng hiện tại của người dùng:\n");
        sb.append("• Chiều cao: ").append(nvl(p.getHeightCm(), "?")).append(" cm\n");
        sb.append("• Cân nặng: ").append(nvl(p.getWeightKg(), "?")).append(" kg\n");
        sb.append("• Tuổi: ").append(nvl(p.getAge(), "?")).append("\n");
        sb.append("• Giới tính: ").append(nvl(p.getGender(), "?")).append("\n");
        sb.append("• Mục tiêu: ").append(nvl(p.getGoal(), "?")).append("\n");
        sb.append("• Mức độ vận động: ").append(nvl(p.getActivityLevel(), "?")).append("\n");

        if (p.getConditions() != null && !p.getConditions().isEmpty()) {
            sb.append("• Bệnh nền: ")
                    .append(String.join(", ", p.getConditions()))
                    .append("\n");
        }
        if (p.getAllergies() != null && !p.getAllergies().isEmpty()) {
            sb.append("• Dị ứng: ")
                    .append(String.join(", ", p.getAllergies()))
                    .append("\n");
        }
        if (p.getNutritionRules() != null && !p.getNutritionRules().isEmpty()) {
            sb.append("• Một số rule dinh dưỡng áp dụng: ")
                    .append(String.join("; ", p.getNutritionRules()))
                    .append("\n");
        }

        return sb.toString();
    }

    private static String nvl(Object v, String fallback) {
        return v == null ? fallback : v.toString();
    }

}
