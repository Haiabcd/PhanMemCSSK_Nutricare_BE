package com.hn.nutricarebe.service;

import java.util.List;

import com.hn.nutricarebe.entity.RefreshToken;

public interface RefreshTokenService {
    void saveRefreshToken(RefreshToken r);
    // Vô hiệu hóa tất cả "token family"
    void revokeFamily(String familyId);

    void saveAll(List<RefreshToken> tokens);

    RefreshToken findById(String id);
}
