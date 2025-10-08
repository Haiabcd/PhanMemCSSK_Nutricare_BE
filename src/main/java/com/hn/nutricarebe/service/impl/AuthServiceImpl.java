package com.hn.nutricarebe.service.impl;

import com.hn.nutricarebe.dto.request.MealPlanCreationRequest;
import com.hn.nutricarebe.dto.request.OnboardingRequest;
import com.hn.nutricarebe.dto.request.UserAllergyCreationRequest;
import com.hn.nutricarebe.dto.request.UserConditionCreationRequest;
import com.hn.nutricarebe.dto.response.*;
import com.hn.nutricarebe.entity.RefreshToken;
import com.hn.nutricarebe.entity.User;
import com.hn.nutricarebe.enums.*;
import com.hn.nutricarebe.exception.AppException;
import com.hn.nutricarebe.exception.ErrorCode;
import com.hn.nutricarebe.helper.GoogleLoginHelper;
import com.hn.nutricarebe.service.*;
import com.hn.nutricarebe.utils.PkceStore;
import com.hn.nutricarebe.utils.PkceUtil;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriComponentsBuilder;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;


@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class AuthServiceImpl implements AuthService {
    ProfileService profileService;
    UserAllergyService userAllergyService;
    MealPlanDayService mealPlanDayService;
    UserConditionService userConditionService;
    UserService userService;
    PkceStore pkceStore;
    WebClient webClient;
    RefreshTokenService refreshTokenService;

    @NonFinal
    @Value("${supabase.host}")
    String SUPABASE_HOST;

    @NonFinal
    @Value("${oauth.callback-url}")
    String CALLBACK_URL;

    @NonFinal
    @Value("${jwt.signerKey}")
    protected String SIGNER_KEY;

    @NonFinal
    @Value("${supabase.anon-key}")
    String SUPABASE_ANON_KEY;

    @NonFinal
    @Value("${jwt.valid-duration}")
    protected long ACCESS_TTL_SECONDS;

    @NonFinal
    @Value("${jwt.refreshable-duration}")
    protected long REFRESH_TTL_SECONDS;

    @Override
    @Transactional
    public OnboardingResponse onBoarding(OnboardingRequest request) {
        //B1: Lưu user
        User savedUser = userService.saveOnboarding(request.getDeviceId());
        //B2: Lưu profile
        profileService.save(request.getProfile(), savedUser);
        //B3: Lưu bệnh nền
        Set<UUID> conditionIds = request.getConditions();
        if(conditionIds != null && !conditionIds.isEmpty()){
            userConditionService.saveUserCondition(UserConditionCreationRequest.builder()
                    .user(savedUser)
                    .conditionIds(conditionIds)
                    .build());
        }
        //B4: Lưu dị ứng
        Set<UUID> allergyIds = request.getAllergies();
        if(allergyIds != null && !allergyIds.isEmpty()){
            userAllergyService.saveUserAllergy(UserAllergyCreationRequest.builder()
                    .user(savedUser)
                    .allergyIds(allergyIds)
                    .build());
        }
        //B5: Lập kế hoạch tuần (MealPlanDay - 7 ngày)
        mealPlanDayService.createPlan(
                MealPlanCreationRequest.builder()
                        .userId(savedUser.getId())
                        .profile(request.getProfile())
                        .build(), 7
        );
        //B6: Lập kế hoạch chi tiết (MealPlanItem)


        //B7: Tạo token
        String familyId = UUID.randomUUID().toString();

        String access  = createAccessToken(savedUser);
        String refresh = createRefreshToken(savedUser, familyId);

        long accessExp = getClaims(parseJwt(access)).getExpirationTime().toInstant().getEpochSecond();
        long refreshExp = getClaims(parseJwt(refresh)).getExpirationTime().toInstant().getEpochSecond();


        TokenPairResponse tokenPair = TokenPairResponse.builder()
                .tokenType("Bearer")
                .accessToken(access)
                .accessExpiresAt(accessExp)
                .refreshToken(refresh)
                .refreshExpiresAt(refreshExp)
                .build();

        //Lưu refresh token vào DB
        saveRefreshRecord(savedUser.getId(), refresh);


        //Trả về
        return OnboardingResponse.builder()
                .tokenResponse(tokenPair)
                .build();
    }

    @Override
    public Map<String, String> startGoogleOAuth(String device) {
        String myState = UUID.randomUUID().toString();
        //BKCE
        String verifier = PkceUtil.generateCodeVerifier();
        String challenge = PkceUtil.codeChallengeS256(verifier);

        pkceStore.save(myState, verifier);

        // Tạo url callback
        String redirectToWithAppState = UriComponentsBuilder
                .fromHttpUrl(CALLBACK_URL)
                .queryParam("app_state", myState)
                .queryParam("device", device)
                .build(true)
                .toUriString();

        URI authorize = UriComponentsBuilder
                .fromHttpUrl(SUPABASE_HOST + "/auth/v1/authorize")
                .queryParam("provider", "google")
                .queryParam("redirect_to", redirectToWithAppState)
                .queryParam("code_challenge", challenge)  // Mã bảo mật (khóa công khai)
                .queryParam("code_challenge_method", "S256")
                .queryParam("scope", "openid profile email")
                .build()
                .encode()
                .toUri();

        return Map.of(
                "authorizeUrl", authorize.toString(),
                "state", myState
        );
    }


    public SupabaseTokenResponse exchangeCodeForToken(String code, String codeVerifier, String redirectUri) {
        String url = SUPABASE_HOST + "/auth/v1/token?grant_type=pkce";
        Map<String, Object> body = Map.of(
                "auth_code", code,
                "code_verifier", codeVerifier,
                "redirect_to", redirectUri
        );

        try {
            return webClient.post()
                    .uri(url)
                    .header("apikey", SUPABASE_ANON_KEY)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + SUPABASE_ANON_KEY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, resp -> resp.bodyToMono(String.class)
                            .map(msg -> {
                                log.error("Supabase error response: {}", msg);
                                return new RuntimeException("Supabase token exchange failed: " + msg);
                            }))
                    .bodyToMono(SupabaseTokenResponse.class)
                    .block();
        } catch (Exception e) {
            log.error("Error during token exchange: ", e);
            throw new RuntimeException("Token exchange failed", e);
        }
    }

    @Override
    @Transactional
    public LoginProviderResponse googleCallback(String code, String state,String device) {

        String verifier = pkceStore.consume(state);
        if (verifier == null) {
            throw new AppException(ErrorCode.INVALID_OR_EXPIRED_STATE);
        }

        SupabaseTokenResponse tokenRes;
        try {
            // Đổi code lấy token
            tokenRes = exchangeCodeForToken(code, verifier, CALLBACK_URL);
        } catch (Exception e) {
            throw new AppException(ErrorCode.TOKEN_EXCHANGE_FAILED);
        }

        // Lấy thông tin user từ token
        SupabaseUser su = tokenRes.getUser();
        LoginProfile gp = GoogleLoginHelper.parse(su);

        if (gp.getProviderUserId().isBlank()) {
            throw new AppException(ErrorCode.TOKEN_EXCHANGE_FAILED);
        }

        boolean isNewUser = false;

        User user = userService.getUserByProvider(gp.getProviderUserId(), device);

        if (user == null) {
            // Tạo mới user
            user = User.builder()
                    .deviceId((device != null && !device.isBlank()) ? device : null)
                    .email((gp.getEmail() != null && !gp.getEmail().isBlank()) ? gp.getEmail() : null)
                    .providerUserId(gp.getProviderUserId())
                    .provider(Provider.SUPABASE_GOOGLE)
                    .role(Role.USER)
                    .status(UserStatus.ACTIVE)
                    .build();
            isNewUser = true;
        } else {
            // Đã có user
            if (user.getRole() == Role.GUEST) {
                user.setRole(Role.USER);
                user.setProviderUserId(gp.getProviderUserId());
                user.setProvider(Provider.SUPABASE_GOOGLE);
                user.setStatus(UserStatus.ACTIVE);
                if ((user.getDeviceId() == null || user.getDeviceId().isBlank()) && device != null && !device.isBlank()) {
                    user.setDeviceId(device);
                }

                if (user.getEmail() == null || user.getEmail().isBlank() || user.getEmail().equalsIgnoreCase(gp.getEmail())) {
                        user.setEmail((gp.getEmail() != null && !gp.getEmail().isBlank()) ? gp.getEmail() : null);
                }
                profileService.updateAvatarAndName(gp.getAvatar(), gp.getName(), user.getId());
            }
        }
        userService.saveGG(user);

        String familyId = UUID.randomUUID().toString();

        String access  = createAccessToken(user);
        String refresh = createRefreshToken(user, familyId);

        long accessExp = getClaims(parseJwt(access)).getExpirationTime().toInstant().getEpochSecond();
        long refreshExp = getClaims(parseJwt(refresh)).getExpirationTime().toInstant().getEpochSecond();

        // Lưu refresh token vào DB
        saveRefreshRecord(user.getId(), refresh);

        TokenPairResponse tokenPair = TokenPairResponse.builder()
                .tokenType("Bearer")
                .accessToken(access)
                .accessExpiresAt(accessExp)
                .refreshToken(refresh)
                .refreshExpiresAt(refreshExp)
                .build();

        return LoginProviderResponse.builder()
                .tokenResponse(tokenPair)
                .isNewUser(isNewUser)
                .name(gp.getName())
                .urlAvatar(gp.getAvatar())
                .build();
    }

    // Lấy token mới
    @Override
    public TokenPairResponse refresh(String refreshTokenRaw)  {
        // 1. Parse, verify và validate token
        SignedJWT jwt = parseAndVerify(refreshTokenRaw);
        JWTClaimsSet claims = getClaims(jwt);
        validateRefreshClaims(claims);

        String jti = claims.getJWTID();
        UUID sub = UUID.fromString(claims.getSubject());
        String familyId;
        try {
            familyId = claims.getStringClaim("familyId");
        } catch (java.text.ParseException e) {
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }


        // 2. Kiểm tra token trong DB và phát hiện reuse
        RefreshToken tokenOld = refreshTokenService.findById(jti);

        if (tokenOld.isRevoked() || tokenOld.isRotated()) {
            refreshTokenService.revokeFamily(familyId);
            throw new AppException(ErrorCode.REFRESH_TOKEN_REUSED);
        }

        // 3. Lấy user và tạo token mới
        User user = userService.getUserById(sub);

        String newAccessToken = createAccessToken(user);
        String newRefreshToken = createRefreshToken(user, familyId);

        // 4. Rotation: đánh dấu token cũ và lưu token mới
        tokenOld.setRotated(true);

        JWTClaimsSet newClaims = getClaims(parseJwt(newRefreshToken));
        RefreshToken newToken = RefreshToken.builder()
                .jti(newClaims.getJWTID())
                .userId(sub)
                .familyId(familyId)
                .expiresAt(newClaims.getExpirationTime().toInstant())
                .rotated(false)
                .revoked(false)
                .build();

        tokenOld.setReplacedByJti(newToken.getJti());
        refreshTokenService.saveAll(List.of(tokenOld, newToken));

        // 5. Trả về response
        long accessExp = getClaims(parseJwt(newAccessToken))
                .getExpirationTime().toInstant().getEpochSecond();
        long refreshExp = newClaims.getExpirationTime().toInstant().getEpochSecond();

        return TokenPairResponse.builder()
                .tokenType("Bearer")
                .accessToken(newAccessToken)
                .accessExpiresAt(accessExp)
                .refreshToken(newRefreshToken)
                .refreshExpiresAt(refreshExp)
                .build();

    }

    /* ----------------- Helper methods ----------------- */

    private void validateRefreshClaims(JWTClaimsSet claims) {
        try {
            if (!"nutricare.com".equals(claims.getIssuer()) ||
                    !"refresh".equals(claims.getStringClaim("typ")) ||
                    claims.getJWTID() == null ||
                    claims.getSubject() == null ||
                    claims.getStringClaim("familyId") == null) {
                throw new AppException(ErrorCode.INVALID_TOKEN);
            }

            Instant exp = claims.getExpirationTime().toInstant();
            if (Instant.now().isAfter(exp.plusSeconds(60))) {
                throw new AppException(ErrorCode.EXPIRED_REFRESH_TOKEN);
            }
        } catch (java.text.ParseException e) {
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }
    }

    private SignedJWT parseJwt(String raw) {
        try {
            return SignedJWT.parse(raw);
        } catch (java.text.ParseException e) {
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }
    }

    private SignedJWT parseAndVerify(String raw) {
        try {
            SignedJWT jwt = SignedJWT.parse(raw);
            if (!jwt.verify(new MACVerifier(SIGNER_KEY.getBytes(StandardCharsets.UTF_8)))) {
                throw new AppException(ErrorCode.INVALID_SIGNATURE);
            }
            return jwt;
        } catch (ParseException | JOSEException e) {
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }
    }

    private JWTClaimsSet getClaims(SignedJWT jwt) {
        try {
            return jwt.getJWTClaimsSet();
        } catch (ParseException e) {
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }
    }

   //Tạo token
    private String createAccessToken(User user) {
        Instant now = Instant.now();
        Instant exp = now.plus(ACCESS_TTL_SECONDS, ChronoUnit.SECONDS);

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .jwtID(UUID.randomUUID().toString())
                .subject(user.getId().toString())
                .issuer("nutricare.com")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(exp))
                .claim("typ", "access")
                .claim("scope", user.getRole().toString())
                .build();

        return sign(claims);
    }

    private String createRefreshToken(User user, String familyId) {
        Instant now = Instant.now();
        Instant exp = now.plus(REFRESH_TTL_SECONDS, ChronoUnit.SECONDS);

        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .jwtID(UUID.randomUUID().toString())
                .subject(user.getId().toString())
                .issuer("nutricare.com")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(exp))
                .claim("typ", "refresh")
                .claim("familyId", familyId) // chống reuse theo “token family”
                .build();

        return sign(claims);
    }

    private String sign(JWTClaimsSet claims) {
        try {
            JWSObject jws = new JWSObject(new JWSHeader(JWSAlgorithm.HS512), new Payload(claims.toJSONObject()));
            jws.sign(new MACSigner(SIGNER_KEY.getBytes()));
            return jws.serialize();
        } catch (JOSEException e) {
            throw new RuntimeException("Sign token failed: " + e.getMessage(), e);
        }
    }

    private void saveRefreshRecord(UUID userId, String refreshToken) {
        var claims = getClaims(parseJwt(refreshToken));
        refreshTokenService.saveRefreshToken(RefreshToken.builder()
                .jti(claims.getJWTID())
                .userId(userId)
                .familyId(getFamilyIdSafe(claims))
                .expiresAt(claims.getExpirationTime().toInstant())
                .rotated(false)
                .revoked(false)
                .build());
    }

    private String getFamilyIdSafe(JWTClaimsSet claims) {
        try {
            String fid = claims.getStringClaim("familyId");
            if (fid == null) throw new AppException(ErrorCode.INVALID_TOKEN);
            return fid;
        } catch (ParseException e) {
            throw new AppException(ErrorCode.INVALID_TOKEN);
        }
    }



}
