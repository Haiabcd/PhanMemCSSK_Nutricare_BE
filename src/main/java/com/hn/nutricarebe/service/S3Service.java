package com.hn.nutricarebe.service;

import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;

public interface S3Service {
    /**
     * Upload file từ backend lên S3 (bucket public).
     * @param file multipart file upload từ client
     * @param keyPrefix ví dụ: "images/foods/{foodId}" hoặc "images/ingredients/{ingredientId}"
     * @return objectKey đã lưu trên S3
     */
    String uploadObject(MultipartFile file, String keyPrefix) throws IOException;
}
