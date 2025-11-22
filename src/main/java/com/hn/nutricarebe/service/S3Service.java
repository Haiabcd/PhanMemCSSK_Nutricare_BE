package com.hn.nutricarebe.service;

import java.io.IOException;

import org.springframework.web.multipart.MultipartFile;

public interface S3Service {
    String uploadObject(MultipartFile file, String keyPrefix) throws IOException;
    void deleteObject(String objectKey);
}
