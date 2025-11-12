package com.hn.nutricarebe.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.hn.nutricarebe.dto.ai.*;
import com.hn.nutricarebe.service.ChatService;
import com.hn.nutricarebe.service.IngredientService;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequestMapping("/ai")
public class ChatController {
    ChatService chatService;
    IngredientService ingredientService;

    @PostMapping("/chat")
    String chat(@ModelAttribute ChatRequest request) {
        return chatService.chat(request.getFile(), request.getMessage());
    }

    @PostMapping("/description-suggestion")
    String suggestion(@ModelAttribute SuggestionAI request) {
        return chatService.writeDishDescription(request);
    }

    @PostMapping("/add-rule")
    void addRule(@RequestBody CreationRuleAI request) {
        chatService.addRule(request);
    }

    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public NutritionAudit audit(
            @RequestParam("image") MultipartFile image,
            @RequestParam(value = "hint", required = false) String hint,
            @RequestParam(value = "strict", defaultValue = "false") boolean strict) {
        DishVisionResult vision = chatService.analyzeDishFromImage(image, hint);
        return ingredientService.audit(vision, strict);
    }

}
