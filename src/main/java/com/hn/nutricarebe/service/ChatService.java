package com.hn.nutricarebe.service;



import org.springframework.web.multipart.MultipartFile;
import com.hn.nutricarebe.dto.ai.CreationRuleAI;
import com.hn.nutricarebe.dto.ai.DishVisionResult;
import com.hn.nutricarebe.dto.ai.SuggestionAI;


public interface ChatService {
    String chat(MultipartFile file, String message);

    String writeDishDescription(SuggestionAI request);

    void addRule(CreationRuleAI request);

    DishVisionResult analyzeDishFromImage(MultipartFile image, String hint);
}
