package com.hn.nutricarebe.service.impl;

import com.hn.nutricarebe.entity.RefreshToken;
import com.hn.nutricarebe.exception.AppException;
import com.hn.nutricarebe.exception.ErrorCode;
import com.hn.nutricarebe.repository.RefreshTokenRepository;
import com.hn.nutricarebe.service.RefreshTokenService;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Service;

import java.util.List;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@AllArgsConstructor
@Service
public class RefreshTokenServiceImpl implements RefreshTokenService {
    RefreshTokenRepository refreshTokenRepository;

    @Override
    public void revokeFamily(String familyId) {
        List<RefreshToken> tokens = refreshTokenRepository.findByFamilyId(familyId);
        tokens.forEach(t -> t.setRevoked(true));
        refreshTokenRepository.saveAll(tokens);
    }

    @Override
    public RefreshToken findById(String id) {
        return refreshTokenRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.REFRESH_TOKEN_NOT_FOUND));
    }

    @Override
    public void saveAll(List<RefreshToken> tokens) {
        refreshTokenRepository.saveAll(tokens);
    }

    @Override
    public void saveRefreshToken(RefreshToken r) {
        refreshTokenRepository.save(r);
    }
}

