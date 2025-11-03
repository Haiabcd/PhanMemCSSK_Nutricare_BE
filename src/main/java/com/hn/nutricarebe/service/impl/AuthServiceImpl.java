package com.hn.nutricarebe.service.impl;

import com.hn.nutricarebe.dto.request.*;
import com.hn.nutricarebe.dto.response.*;
import com.hn.nutricarebe.entity.Profile;
import com.hn.nutricarebe.entity.RefreshToken;
import com.hn.nutricarebe.entity.User;
import com.hn.nutricarebe.enums.*;
import com.hn.nutricarebe.exception.AppException;
import com.hn.nutricarebe.exception.ErrorCode;
import com.hn.nutricarebe.helper.GoogleLoginHelper;
import com.hn.nutricarebe.mapper.ProfileMapper;
import com.hn.nutricarebe.repository.ProfileRepository;
import com.hn.nutricarebe.repository.UserRepository;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    ProfileMapper profileMapper;
    ProfileRepository profileRepository;
    UserRepository userRepository;

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
        Profile profile = profileMapper.toProfile(request.getProfile());
        profile.setUser(savedUser);
        if(savedUser.getProvider() == Provider.SUPABASE_GOOGLE){
            profile.setAvatarUrl(savedUser.getProviderImageUrl());
        }
        profileRepository.save(profile);
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
        //B5: Lập kế hoạch(7 ngày)
        mealPlanDayService.createPlan(
                MealPlanCreationRequest.builder()
                        .userId(savedUser.getId())
                        .profile(request.getProfile())
                        .build(), 7
        );

        //B6: Tạo token
        String access  = createAccessToken(savedUser);
        String refresh = createRefreshToken(savedUser, UUID.randomUUID().toString());
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

    //============================ Auth GG ============================//
    @Override
    public Map<String, String> startGoogleOAuth(String device,Boolean upgrade) {
        String myState = UUID.randomUUID().toString();
        //BKCE
        String verifier = PkceUtil.generateCodeVerifier();
        String challenge = PkceUtil.codeChallengeS256(verifier);

        pkceStore.save(myState, verifier);

        // Tạo url callback
        String redirectToWithAppState = UriComponentsBuilder
                .fromUriString(CALLBACK_URL)
                .queryParam("app_state", myState)
                .queryParam("device", device)
                .queryParam("upgrade", upgrade)
                .build(true)
                .toUriString();

        URI authorize = UriComponentsBuilder
                .fromUriString(SUPABASE_HOST)
                .path("/auth/v1/authorize")
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
    public GoogleCallbackResponse googleCallback(String code, String state,String device, Boolean upgrade) {
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
        User user = userRepository.findByProviderUserId(gp.getProviderUserId()).orElse(null);

        AuthFlowOutcome outcome;
        String familyId = UUID.randomUUID().toString();
        String access = null;
        String refresh = null;
        long accessExp = 0;
        long refreshExp = 0;


        if (user == null) {
            Optional<User> userDevice = userRepository.findTopByDeviceIdAndStatusOrderByCreatedAtDesc(device, UserStatus.ACTIVE);
            if(userDevice.isPresent() && userDevice.get().getRole() == Role.GUEST){
                user = new User();
                user.setRole(Role.USER);
                user.setProviderUserId(gp.getProviderUserId());
                user.setProvider(Provider.SUPABASE_GOOGLE);
                user.setStatus(UserStatus.ACTIVE);
                user.setDeviceId(null);
                if (user.getEmail() == null || user.getEmail().isBlank() || user.getEmail().equalsIgnoreCase(gp.getEmail())) {
                    user.setEmail((gp.getEmail() != null && !gp.getEmail().isBlank()) ? gp.getEmail() : null);
                }
                Profile profile = profileRepository.findByUser_Id(user.getId())
                        .orElseThrow(() -> new AppException(ErrorCode.PROFILE_NOT_FOUND));
                profile.setName(gp.getName());
                profile.setAvatarUrl(gp.getAvatar());
                profileRepository.save(profile);
                outcome = AuthFlowOutcome.GUEST_UPGRADE;
            }else{
                user = User.builder()
                        .deviceId(null)
                        .email((gp.getEmail() != null && !gp.getEmail().isBlank()) ? gp.getEmail() : null)
                        .providerUserId(gp.getProviderUserId())
                        .provider(Provider.SUPABASE_GOOGLE)
                        .role(Role.USER)
                        .providerImageUrl(gp.getAvatar())
                        .status(UserStatus.ACTIVE)
                        .build();
                outcome = AuthFlowOutcome.FIRST_TIME_GOOGLE;
            }
        } else {
            if(user.getRole() == Role.USER && upgrade == Boolean.FALSE) {
                Profile profile = profileRepository.findByUser_Id(user.getId()).orElse(null);
                if (profile != null) {
                    outcome = AuthFlowOutcome.RETURNING_GOOGLE;
                    access = createAccessToken(user);
                    refresh = createRefreshToken(user, familyId);
                    accessExp = getClaims(parseJwt(access)).getExpirationTime().toInstant().getEpochSecond();
                    refreshExp = getClaims(parseJwt(refresh)).getExpirationTime().toInstant().getEpochSecond();
                    saveRefreshRecord(user.getId(), refresh);
                } else {
                    outcome = AuthFlowOutcome.FIRST_TIME_GOOGLE;
                }
            }else{
                throw new AppException(ErrorCode.PROVIDER_ALREADY_LINKED);
            }
        }
        userService.saveGG(user);
        TokenPairResponse tokenPair = TokenPairResponse.builder()
                .tokenType("Bearer")
                .accessToken(access)
                .accessExpiresAt(accessExp)
                .refreshToken(refresh)
                .refreshExpiresAt(refreshExp)
                .build();

        return GoogleCallbackResponse.builder()
                .outcome(outcome)
                .tokenResponse(tokenPair)
                .build();
    }
    //============================ Auth GG ==============================//

    //============================ Cấp token ============================//
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
    //============================ Cấp token ============================//


    //============================ Logout ============================//
    @Override
    @Transactional
    public void logout(String refreshTokenRaw) {
        SignedJWT jwt = parseAndVerify(refreshTokenRaw);
        JWTClaimsSet claims = getClaims(jwt);
        validateRefreshClaims(claims);
        String jti = claims.getJWTID();
        RefreshToken token = refreshTokenService.findById(jti);
        if (token.isRevoked() || token.isRotated()) {
            return;
        }
        token.setRevoked(true);
        User user = userService.getUserById(UUID.fromString(claims.getSubject()));
        if(user.getRole() == Role.USER){
            user.setDeviceId(null);
            userRepository.save(user);
        }
        refreshTokenService.saveAll(List.of(token));
    }
    //============================ Logout ============================//

    @Override
    public AdminLoginResponse authenticate(AdminLoginRequest request){
        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);
        var user = userRepository.findByUsernameIgnoreCase(request.getUsername())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NAME_NOT_FOUND));

        boolean authenticated = passwordEncoder.matches(request.getPasswordHash(), user.getPasswordHash());

        if (!authenticated)
            throw new AppException(ErrorCode.PASSWORD_INCORRECT);

        String familyId = UUID.randomUUID().toString();
        String access  = createAccessToken(user);
        String refresh = createRefreshToken(user, familyId);
        long accessExp = getClaims(parseJwt(access)).getExpirationTime().toInstant().getEpochSecond();
        long refreshExp = getClaims(parseJwt(refresh)).getExpirationTime().toInstant().getEpochSecond();

        saveRefreshRecord(user.getId(), refresh);

        return AdminLoginResponse.builder()
                .accessToken(access)
                .accessExpiresAt(accessExp)
                .refreshToken(refresh)
                .refreshExpiresAt(refreshExp)
                .build();
    }

    @Override
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public void updateAdminCredentials(AdminCredentialUpdateRequest request) {
        var user = userRepository.findByUsernameIgnoreCase(request.getUsername())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NAME_NOT_FOUND));

        PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);
        if (!passwordEncoder.matches(request.getPasswordOld(), user.getPasswordHash())) {
            throw new AppException(ErrorCode.PASSWORD_INCORRECT);
        }
        boolean changed = false;

        // 4) Đổi username (nếu có)
        String newUsername = request.getNewUsername();
        if (newUsername != null && !newUsername.isBlank()
                && !newUsername.equalsIgnoreCase(user.getUsername())) {
            userRepository.findByUsernameIgnoreCase(newUsername).ifPresent(u -> {
                throw new AppException(ErrorCode.USERNAME_EXISTED);
            });
            user.setUsername(newUsername);
            changed = true;
        }
        String newPassword = request.getNewPassword();
        if (newPassword != null && !newPassword.isBlank()) {
            if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
                throw new AppException(ErrorCode.PASSWORD_SAME_AS_OLD);
            }
            user.setPasswordHash(passwordEncoder.encode(newPassword));
            changed = true;
        }
        if (changed) {
            userRepository.save(user);
        }
    }


    /* ------------------------- Helper methods -------------------------*/
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
    //============================ Tạo token & refresh token =============//
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
    //============================ Tạo token & refresh token =============//
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
