package com.hn.nutricarebe.service.impl;

import com.hn.nutricarebe.exception.AppException;
import com.hn.nutricarebe.exception.ErrorCode;
import com.hn.nutricarebe.service.S3Service;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import java.io.IOException;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3ServiceImpl implements S3Service {
    private final S3Client s3Client;

    @Value("${aws.s3.bucket.public}")
    private String publicBucket;

    @Override
    public String uploadObject(MultipartFile file, String keyPrefix) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new AppException(ErrorCode.FILE_EMPTY);
        }

        final String contentType = Objects.requireNonNullElse(file.getContentType(), "application/octet-stream");
        final String ext = guessExtension(contentType); // ".jpg" | ".png" | ".webp" | ".bin"
        final String objectKey = buildKey(keyPrefix, ext);

        PutObjectRequest putReq = PutObjectRequest.builder()
                .bucket(publicBucket)
                .key(objectKey)
                .contentType(contentType)
                .cacheControl("public, max-age=31536000, immutable") //cache 1 nam
                .build();

        s3Client.putObject(putReq, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
        return objectKey;
    }


    @Override
    public void deleteObject(String objectKey) {
        if (objectKey == null || objectKey.trim().isEmpty()) {
            return;
        }
        try {
            DeleteObjectRequest deleteReq = DeleteObjectRequest.builder()
                    .bucket(publicBucket)
                    .key(objectKey)
                    .build();

            s3Client.deleteObject(deleteReq);
        } catch (Exception e) {
            throw new AppException(ErrorCode.DELETE_OBJECT_FAILED);
        }
    }

    private static String buildKey(String keyPrefix, String ext) {
        String cleanPrefix = keyPrefix;
        if (cleanPrefix.startsWith("/")) cleanPrefix = cleanPrefix.substring(1);
        if (cleanPrefix.endsWith("/")) cleanPrefix = cleanPrefix.substring(0, cleanPrefix.length() - 1);
        String id = UUID.randomUUID().toString();
        return cleanPrefix + "/" + id + ext;
    }

    private static String guessExtension(String contentType) {
        if (contentType == null) return ".bin";
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/png"  -> ".png";
            case "image/webp" -> ".webp";
            default -> ".bin";
        };
    }
}
