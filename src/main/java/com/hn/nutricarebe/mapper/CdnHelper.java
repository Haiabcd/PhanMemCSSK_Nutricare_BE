package com.hn.nutricarebe.mapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CdnHelper {
    @Value("${cdn.base-url}")
    private String cdnBaseUrl;

    public String buildUrl(String key) {
        if (key == null) return null;
        String base = cdnBaseUrl;
        if (!base.endsWith("/")) base += "/";
        return base + key;
    }
}
