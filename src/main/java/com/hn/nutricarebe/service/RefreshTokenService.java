package com.hn.nutricarebe.service;

import com.hn.nutricarebe.entity.RefreshToken;

import java.util.List;

public interface RefreshTokenService {
    public void saveRefreshToken(RefreshToken r);
    //Vô hiệu hóa tất cả "token family"
    public void revokeFamily(String familyId);
    public void saveAll(List<RefreshToken> tokens);
    public RefreshToken findById(String id);
}
