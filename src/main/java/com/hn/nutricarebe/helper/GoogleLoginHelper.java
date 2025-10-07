package com.hn.nutricarebe.helper;

import com.hn.nutricarebe.dto.response.LoginProfile;
import com.hn.nutricarebe.dto.response.SupabaseIdentity;
import com.hn.nutricarebe.dto.response.SupabaseUser;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class GoogleLoginHelper {
    private GoogleLoginHelper() {}

    public static LoginProfile parse(SupabaseUser u) {
        Map<String, Object> meta = (u != null && u.getUser_metadata() != null)
                ? u.getUser_metadata() : Map.of();

        // --- providerUserId: ưu tiên identities.provider_id ---
        String providerUserId = Optional.ofNullable(u)
                .map(SupabaseUser::getIdentities)
                .flatMap(ids -> ids.stream()
                        .filter(id -> "google".equalsIgnoreCase(safeStr(id.getProvider())))
                        .map(SupabaseIdentity::getProvider_id)
                        .filter(s -> s != null && !s.isBlank())
                        .findFirst())
                .orElseGet(() ->
                        safeStr((String) meta.getOrDefault("provider_id",
                                (String) meta.getOrDefault("sub", "")))
                );

        // --- email ---
        String email = Optional.ofNullable(u)
                .map(SupabaseUser::getEmail)
                .filter(s -> s != null && !s.isBlank())
                .orElseGet(() -> safeStr((String) meta.get("email")));

        // --- emailVerified ---
        boolean emailVerified = Optional.ofNullable(u)
                .map(SupabaseUser::getIdentities)
                .flatMap(ids -> ids.stream()
                        .filter(id -> "google".equalsIgnoreCase(safeStr(id.getProvider())))
                        .map(SupabaseIdentity::getEmail_verified)
                        .filter(Objects::nonNull)
                        // .filter(Boolean.TRUE::equals)         // (tuỳ chọn) nếu muốn chỉ true
                        .findFirst())
                .orElseGet(() -> toBoolean(meta.get("email_verified")));

        // --- name ---
        String name = safeStr((String) meta.getOrDefault("full_name",
                (String) meta.getOrDefault("name", "")));

        // --- avatar ---
        String avatar = safeStr((String) meta.getOrDefault("avatar_url",
                (String) meta.getOrDefault("picture", "")));

        return LoginProfile.builder()
                .providerUserId(providerUserId)
                .email(email)
                .name(name)
                .avatar(avatar)
                .emailVerified(emailVerified)
                .build();
    }

    private static String safeStr(String s) { return (s == null) ? "" : s.trim(); }

    private static boolean toBoolean(Object v) {
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) return s.equalsIgnoreCase("true");
        if (v instanceof Number n) return n.intValue() != 0;
        return false;
    }
}
