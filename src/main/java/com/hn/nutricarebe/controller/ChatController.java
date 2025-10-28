package com.hn.nutricarebe.controller;

import com.hn.nutricarebe.dto.ai.CreationRuleAI;
import com.hn.nutricarebe.dto.ai.NutritionRuleAI;
import com.hn.nutricarebe.dto.ai.SuggestionAI;
import com.hn.nutricarebe.dto.ai.ChatRequest;
import com.hn.nutricarebe.service.ChatService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequestMapping("/ai")
public class ChatController {
    ChatService chatService;


    @PostMapping("/chat")
    String chat(@ModelAttribute ChatRequest request) {
        return chatService.chat(request.getFile(),request.getMessage());
    }

    @PostMapping("/suggestion")
    String suggestion(@ModelAttribute SuggestionAI request) {
        return chatService.writeDishDescription(request);
    }

    @PostMapping("/add-rule")
    void addRule(@RequestBody CreationRuleAI request) {
        chatService.addRule(request);
    }
}
