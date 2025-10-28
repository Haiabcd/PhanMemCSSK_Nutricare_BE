package com.hn.nutricarebe.service;

import com.hn.nutricarebe.dto.ai.CreationRuleAI;
import com.hn.nutricarebe.dto.ai.NutritionRuleAI;
import com.hn.nutricarebe.dto.ai.SuggestionAI;
import com.hn.nutricarebe.dto.ai.TagCreationRequest;
import com.hn.nutricarebe.entity.Nutrition;
import com.hn.nutricarebe.entity.Tag;
import com.hn.nutricarebe.enums.RuleScope;
import com.hn.nutricarebe.enums.TargetType;
import com.hn.nutricarebe.repository.TagRepository;
import com.hn.nutricarebe.service.tools.ProfileTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.memory.repository.jdbc.JdbcChatMemoryRepository;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ChatService {
    private final ChatClient chatClient;
    private final JdbcChatMemoryRepository jdbcChatMemoryRepository;
    private final TagService tagService;
    private final NutritionRuleService nutritionRuleService;


    // === SYSTEM PROMPT NGẮN + HƯỚNG DẪN GỌI TOOL ===
    private static final String NUTRICARE_SYSTEM_PROMPT = """
    Bạn là NutriCare Assistant – trợ lý dinh dưỡng.
    Luôn dùng đơn vị: kcal, proteinG, carbG, fatG, fiberG, sodiumMg, sugarMg.
    Khi cần dữ liệu hồ sơ, HÃY GỌI tool getProfileSummary() và THỰC THI tool; KHÔNG in ra mã hoặc 'tool_code'.
    Nếu thiếu dữ liệu quan trọng, hỏi 1–2 câu ngắn rồi mới đề xuất chi tiết.
    """;
    private static final String DISH_COPYWRITER_SYSTEM_PROMPT = """
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
    private static final String ADD_RULE_SYSTEM_PROMPT = """
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

    public ChatService(ChatClient.Builder builder,
                       JdbcChatMemoryRepository jdbcChatMemoryRepository,
                       ProfileTool profileTool,
                       TagService tagService,
                       NutritionRuleService nutritionRuleService
    ) {
        this.jdbcChatMemoryRepository = jdbcChatMemoryRepository;
        this.tagService = tagService;
        this.nutritionRuleService = nutritionRuleService;

        ChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(jdbcChatMemoryRepository)
                .maxMessages(30)
                .build();

        chatClient = builder
                .defaultAdvisors(MessageChatMemoryAdvisor.builder(chatMemory).build())
                .defaultTools(profileTool) //Đăng ký tool
                .build();
    }


    public String chat(MultipartFile file, String message) {
//        var auth = SecurityContextHolder.getContext().getAuthentication();
//        if (auth == null || !auth.isAuthenticated()) {
//            throw new AppException(ErrorCode.UNAUTHORIZED);
//        }
//        UUID userId = UUID.fromString(auth.getName());

        UUID userId = UUID.fromString("6b2df221-c835-47b7-a527-43c40acbd0df");
        String conversationId =  userId.toString();

        ChatOptions chatOptions = ChatOptions.builder()
                .temperature(0D)
                .build();

        var prompt  = chatClient
                .prompt()
                .options(chatOptions)
                .system(NUTRICARE_SYSTEM_PROMPT)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId));

        if (file != null && !file.isEmpty() && file.getContentType() != null) {
            var media = Media.builder()
                    .mimeType(MimeTypeUtils.parseMimeType(file.getContentType()))
                    .data(file.getResource())
                    .build();
            prompt .user(u -> u.media(media).text(message));
        } else {
            prompt .user(message);
        }
        return prompt .call().content();
    }


    public String writeDishDescription(SuggestionAI request) {

        String conversationId = "ADMIN";

        ChatOptions opts = ChatOptions.builder()
                .temperature(0.7D)
                .build();

        var prompt = chatClient
                .prompt()
                .options(opts)
                .system(DISH_COPYWRITER_SYSTEM_PROMPT)
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId));

        // Xây user message: đưa tên món + mục tiêu + giới hạn độ dài + dinh dưỡng
        StringBuilder userMsg = new StringBuilder();
        userMsg.append("Yêu cầu: Viết mô tả món ăn ngắn gọn, thân thiện, tăng cảm giác ngon miệng.\n");
        userMsg.append("Tên món: ").append(request.getDishName() == null ? "Không rõ" : request.getDishName()).append("\n");
        if (request.getNutrition() != null) {
            Nutrition nutrition = request.getNutrition();
            userMsg.append("Dinh dưỡng (nếu thấy phù hợp chỉ nên nhắc nhẹ 1 câu):\n")
                    .append("- kcal: ").append(nutrition.getKcal()).append("\n")
                    .append("- proteinG: ").append(nutrition.getProteinG()).append("\n")
                    .append("- carbG: ").append(nutrition.getCarbG()).append("\n")
                    .append("- fatG: ").append(nutrition.getFatG()).append("\n")
                    .append("- fiberG: ").append(nutrition.getFiberG()).append("\n");
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


    public void addRule(CreationRuleAI request) {
        ChatOptions chatOptions = ChatOptions.builder().temperature(0D).build();
        // Lấy từ điển đã cache sẵn
        String tagVocabulary = tagService.getTagVocabularyText();

        SystemMessage systemMessage = new SystemMessage(ADD_RULE_SYSTEM_PROMPT + "\n" + tagVocabulary);

        UserMessage userMessage = new UserMessage(request.getMessage());
        Prompt prompt = new Prompt(systemMessage, userMessage);

        List<NutritionRuleAI> rules = chatClient
                .prompt(prompt)
                .options(chatOptions)
                .call()
                .entity(new ParameterizedTypeReference<List<NutritionRuleAI>>() {});
        if (rules == null) rules = List.of();

        for (NutritionRuleAI r : rules) {
            normalizeFoodTags(r);
        }
        nutritionRuleService.saveRules(request, rules);
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
        Set<String> inputCodes  = new HashSet<>();
        if (r.getFoodTags() != null) inputCodes.addAll(r.getFoodTags());
        Map<String,String> customMap = new HashMap<>();
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
        Set<String> known = new HashSet<>(existing.stream().map(this::toNameCode).toList());
        Set<TagCreationRequest> custom = normalized.stream()
                .filter(c -> !known.contains(c))
                .map(c -> TagCreationRequest.builder().nameCode(c).description(customMap.get(c)).build())
                .collect(Collectors.toSet());
        r.setFoodTags(known);
        r.setCustomFoodTags(custom);
    }

    private String toNameCode(String s) {
        if (s == null) return null;
        return s.trim()
                .replaceAll("[^A-Za-z0-9]+", "_")   // non-alnum -> underscore
                .replaceAll("_+", "_")              // gộp _
                .replaceAll("^_|_$", "")            // bỏ _ đầu/cuối
                .toLowerCase();
    }

}
