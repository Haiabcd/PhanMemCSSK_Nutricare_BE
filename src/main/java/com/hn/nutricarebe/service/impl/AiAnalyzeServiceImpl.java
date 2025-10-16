// src/main/java/com/hn/nutricarebe/service/impl/AiAnalyzeServiceImpl.java
package com.hn.nutricarebe.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.hn.nutricarebe.ai.LogMealClient;
import com.hn.nutricarebe.dto.request.AnalyzeByUrlRequest;
import com.hn.nutricarebe.dto.response.FoodAnalyzeResponse;
import com.hn.nutricarebe.dto.response.NutritionResponse;
import com.hn.nutricarebe.exception.AppException;
import com.hn.nutricarebe.exception.ErrorCode;
import com.hn.nutricarebe.service.AiAnalyzeService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AiAnalyzeServiceImpl implements AiAnalyzeService {

    LogMealClient logMealClient;

    @Override
    public FoodAnalyzeResponse analyzeByImage(AnalyzeByUrlRequest req) {
        MultipartFile image = req.getImage();
        if (image == null || image.isEmpty()) {
            throw new AppException(ErrorCode.FILE_EMPTY);
        }

        // 1) Nhận diện món (segmentation)
        JsonNode seg = logMealClient.recognizeDish(image);
        log.info("=== SEGMENTATION RESPONSE ===\n{}", seg.toPrettyString());

        long imageId = extractImageId(seg, null);

        // 2) Nutrition (đảm bảo có serving_size & total/per100)
        JsonNode nut = logMealClient.nutrition(imageId);
        log.info("=== NUTRITION RESPONSE ===\n{}", nut.toPrettyString());

        if (imageId <= 0) imageId = nut.path("imageId").asLong(0L);
        if (imageId <= 0) {
            log.error("Cannot determine imageId");
            throw new AppException(ErrorCode.THIRD_PARTY_ERROR);
        }

        // 3) Ingredients (để lấy tên)
        List<Long> ids = new ArrayList<>();
        JsonNode idsNode = nut.path("ids");
        if (idsNode.isArray()) {
            idsNode.forEach(n -> ids.add(n.asLong()));
        }
        JsonNode ing = logMealClient.ingredients(imageId, ids);
        log.info("=== INGREDIENTS RESPONSE ===\n{}", ing.toPrettyString());

        // 4) Map về payload gọn
        String dishName = firstNonNull(
                text(nut.at("/foodName/0"), null),
                text(seg.at("/segmentation_results/0/name"), null),
                text(seg.at("/recognition_results/0/name"), null),
                "Unknown dish"
        );

        Double confidence = firstNonNull(
                dbl(seg.at("/segmentation_results/0/prob"), null),
                dbl(seg.at("/recognition_results/0/prob"), null),
                0.0
        );

        BigDecimal servingGram = dec(nut.path("serving_size"), null);
        NutritionResponse nutrition = mapNutritionForServing(nut, servingGram);
        List<String> ingredientNames = mapIngredientNames(ing, nut);

        return FoodAnalyzeResponse.builder()
                .name(dishName)
                .servingGram(servingGram)
                .nutrition(nutrition)
                .ingredients(ingredientNames)
                .confidence(confidence)
                .build();
    }

    /* ===================== helpers ===================== */

    private long extractImageId(JsonNode seg, JsonNode ingOrNut) {
        long id = seg.path("imageId").asLong(0L);
        if (id <= 0) id = seg.path("image_id").asLong(0L);
        if (id <= 0) id = seg.at("/segmentation_results/0/imageId").asLong(0L);
        if (id <= 0) id = seg.at("/segmentation_results/0/image_id").asLong(0L);
        if (id <= 0) id = seg.at("/imageId").asLong(0L);
        if (id <= 0) id = seg.at("/image_id").asLong(0L);
        if (id <= 0) id = seg.at("/data/imageId").asLong(0L);
        if (id <= 0) id = seg.at("/data/image_id").asLong(0L);

        if (id <= 0 && ingOrNut != null) {
            id = Math.max(id, ingOrNut.path("imageId").asLong(0L));
            id = Math.max(id, ingOrNut.at("/data/imageId").asLong(0L));
        }
        return id;
    }

    private NutritionResponse mapNutritionForServing(JsonNode nutRoot, BigDecimal servingGram) {
        JsonNode total = nutRoot.at("/nutritional_info/totalNutrients");
        if (total.isObject() && total.size() > 0) {
            BigDecimal kcal     = dec(total.at("/ENERC_KCAL/quantity"), BigDecimal.ZERO);
            BigDecimal proteinG = dec(total.at("/PROCNT/quantity"),     BigDecimal.ZERO);
            BigDecimal carbG    = dec(total.at("/CHOCDF/quantity"),     BigDecimal.ZERO);
            BigDecimal fatG     = dec(total.at("/FAT/quantity"),        BigDecimal.ZERO);
            BigDecimal fiberG   = dec(total.at("/FIBTG/quantity"), null);
            BigDecimal sodiumMg = dec(total.at("/NA/quantity"),    null);   // mg
            BigDecimal sugarG   = dec(total.at("/SUGAR/quantity"), null);   // g
            BigDecimal sugarMg  = (sugarG == null) ? null : sugarG.multiply(BigDecimal.valueOf(1000));
            return new NutritionResponse(kcal, proteinG, carbG, fatG, fiberG, sodiumMg, sugarMg);
        }

        JsonNode per100 = nutRoot.at("/nutritional_info/per_100g");
        if (per100.isObject() && per100.size() > 0 && servingGram != null) {
            BigDecimal factor = servingGram.divide(BigDecimal.valueOf(100), 8, RoundingMode.HALF_UP);

            BigDecimal kcal     = dec(per100.at("/ENERC_KCAL/quantity"), BigDecimal.ZERO).multiply(factor);
            BigDecimal proteinG = dec(per100.at("/PROCNT/quantity"),     BigDecimal.ZERO).multiply(factor);
            BigDecimal carbG    = dec(per100.at("/CHOCDF/quantity"),     BigDecimal.ZERO).multiply(factor);
            BigDecimal fatG     = dec(per100.at("/FAT/quantity"),        BigDecimal.ZERO).multiply(factor);
            BigDecimal fiberG   = mulNullable(dec(per100.at("/FIBTG/quantity"), null), factor);
            BigDecimal sodiumMg = mulNullable(dec(per100.at("/NA/quantity"),    null), factor);
            BigDecimal sugarG   = dec(per100.at("/SUGAR/quantity"), null);
            BigDecimal sugarMg  = (sugarG == null) ? null
                    : sugarG.multiply(BigDecimal.valueOf(1000)).multiply(factor);

            return new NutritionResponse(kcal, proteinG, carbG, fatG, fiberG, sodiumMg, sugarMg);
        }

        // Fallback
        return new NutritionResponse(
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                null, null, null
        );
    }

    private static BigDecimal mulNullable(BigDecimal v, BigDecimal k) {
        return (v == null) ? null : v.multiply(k);
    }

    // Lấy danh sách tên nguyên liệu từ nhiều schema có thể có
    private List<String> mapIngredientNames(JsonNode root, JsonNode fallback) {
        List<String> names = new ArrayList<>();
        JsonNode arr = null;
        String[] candidates = {
                "/ingredients",
                "/items/0/ingredients",
                "/recipe",
                "/recipe_per_item/0/recipe"
        };
        for (String p : candidates) {
            JsonNode tryArr = root.at(p);
            if (tryArr.isArray() && tryArr.size() > 0) { arr = tryArr; break; }
        }
        if ((arr == null || !arr.isArray() || arr.size() == 0) && fallback != null) {
            for (String p : candidates) {
                JsonNode tryArr = fallback.at(p);
                if (tryArr.isArray() && tryArr.size() > 0) { arr = tryArr; break; }
            }
        }
        if (arr == null || !arr.isArray()) return names;

        for (JsonNode n : arr) {
            String name = text(n.get("name"), null);
            if (name != null && !name.isBlank()) names.add(name);
        }
        return names;
    }

    /* ===================== tiny utils ===================== */

    private static String text(JsonNode n, String d) {
        if (n == null || n.isNull() || n.isMissingNode()) return d;
        try {
            String s = n.asText();
            return (s == null || s.isEmpty()) ? d : s;
        } catch (Exception e) {
            return d;
        }
    }

    private static Double dbl(JsonNode n, Double d) {
        if (n == null || n.isNull() || n.isMissingNode()) return d;
        try {
            if (n.isNumber()) return n.doubleValue();
            String s = n.asText();
            if (s == null) return d;
            s = s.trim();
            if (s.isEmpty()) return d;
            return Double.valueOf(s);
        } catch (Exception ignore) {
            return d;
        }
    }

    private static BigDecimal dec(JsonNode n, BigDecimal d) {
        try {
            if (n == null || n.isNull() || n.isMissingNode()) return d;
            String s = n.asText();
            if (s == null) return d;
            s = s.trim();
            if (s.isEmpty()) return d;
            return new BigDecimal(s);
        } catch (Exception e) {
            return d;
        }
    }

    @SafeVarargs
    private static <T> T firstNonNull(T... vals) {
        for (T v : vals) if (v != null) return v;
        return null;
    }
}
