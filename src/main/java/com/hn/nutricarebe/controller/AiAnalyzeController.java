package com.hn.nutricarebe.controller;


import com.hn.nutricarebe.dto.request.AnalyzeByUrlRequest;
import com.hn.nutricarebe.dto.response.FoodAnalyzeResponse;
import com.hn.nutricarebe.service.AiAnalyzeService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;



@RestController
@RequestMapping("/meallog-ai")
@FieldDefaults(makeFinal = true, level = lombok.AccessLevel.PRIVATE)
@AllArgsConstructor
public class AiAnalyzeController {
    AiAnalyzeService aiService;

    @PostMapping(
            value = "/analyze-url",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public FoodAnalyzeResponse analyze(@Valid @ModelAttribute AnalyzeByUrlRequest req) {
        return aiService.analyzeByImage(req);
    }

}
