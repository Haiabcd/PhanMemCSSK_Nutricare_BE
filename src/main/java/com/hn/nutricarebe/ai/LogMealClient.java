// src/main/java/com/hn/nutricarebe/ai/LogMealClient.java
package com.hn.nutricarebe.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.client.MultipartBodyBuilder;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class LogMealClient {

    @Value("${logmeal.base-url}") private String baseUrl;
    @Value("${logmeal.key}") private String apiKey;

    private final ObjectMapper om = new ObjectMapper();
    private final RestClient http = RestClient.create();

    /* ===== Helpers ===== */
    private MultiValueMap<String, HttpEntity<?>> buildForm(MultipartFile file) {
        MultipartBodyBuilder b = new MultipartBodyBuilder();

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            throw new RuntimeException("Cannot read multipart bytes", e);
        }

        String original = file.getOriginalFilename();
        String filename = (original != null && !original.isBlank()) ? original : "upload.jpg";

        MediaType ct;
        try {
            String cts = file.getContentType();
            if (cts != null && !cts.isBlank()) {
                ct = MediaType.parseMediaType(cts); // image/jpeg, image/png, ...
            } else if (filename.toLowerCase().endsWith(".png")) {
                ct = MediaType.IMAGE_PNG;
            } else {
                ct = MediaType.IMAGE_JPEG;
            }
        } catch (Exception ignore) {
            ct = MediaType.APPLICATION_OCTET_STREAM;
        }

        org.springframework.core.io.ByteArrayResource res = new org.springframework.core.io.ByteArrayResource(bytes) {
            @Override public String getFilename() { return filename; }
        };

        b.part("image", res)
                .filename(filename)
                .contentType(ct);

        return b.build();
    }

    private JsonNode toJson(ResponseEntity<String> res, String label) {
        try {
            return om.readTree(res.getBody());
        } catch (Exception e) {
            throw new RuntimeException(label + ": " + e.getMessage(), e);
        }
    }

    // 1) Segmentation (multipart) — Upload ảnh và nhận diện món ăn
    public JsonNode recognizeDish(MultipartFile image) {
        // Một số plan có suffix /{modelVersion}; nếu cần thì nối thêm
        String url = baseUrl + "/v2/image/segmentation/complete";
        ResponseEntity<String> res = http.post()
                .uri(url)
                .headers(h -> {
                    h.setBearerAuth(apiKey);
                    h.setAccept(List.of(MediaType.APPLICATION_JSON));
                })
                .contentType(MediaType.MULTIPART_FORM_DATA) // quan trọng với RestClient + MultipartBodyBuilder
                .body(buildForm(image))
                .retrieve()
                .toEntity(String.class);
        return toJson(res, "recognize parse error");
    }

    // 2) Ingredients (JSON) — dùng imageId + ids (danh sách id món trong ảnh)
    public JsonNode ingredients(long imageId, List<Long> ids) {
        String url = baseUrl + "/v2/nutrition/recipe/ingredients";
        Map<String, Object> body = Map.of(
                "imageId", imageId,
                "ids", (ids == null ? List.of() : ids)
        );
        ResponseEntity<String> res = http.post()
                .uri(url)
                .headers(h -> {
                    h.setBearerAuth(apiKey);
                    h.setAccept(List.of(MediaType.APPLICATION_JSON));
                })
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(String.class);
        return toJson(res, "ingredients parse error");
    }

    // 3) Nutrition (JSON) — Lấy thông tin dinh dưỡng
    public JsonNode nutrition(long imageId) {
        String url = baseUrl + "/v2/nutrition/recipe/nutritionalInfo";
        ResponseEntity<String> res = http.post()
                .uri(url)
                .headers(h -> {
                    h.setBearerAuth(apiKey);
                    h.setAccept(List.of(MediaType.APPLICATION_JSON));
                })
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("imageId", imageId))
                .retrieve()
                .toEntity(String.class);
        return toJson(res, "nutrition parse error");
    }
}
