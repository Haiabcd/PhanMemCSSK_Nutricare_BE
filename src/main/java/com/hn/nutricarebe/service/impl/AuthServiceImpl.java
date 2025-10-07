package com.hn.nutricarebe.service.impl;

import com.hn.nutricarebe.dto.request.MealPlanCreationRequest;
import com.hn.nutricarebe.dto.request.OnboardingRequest;
import com.hn.nutricarebe.dto.request.UserAllergyCreationRequest;
import com.hn.nutricarebe.dto.request.UserConditionCreationRequest;
import com.hn.nutricarebe.dto.response.*;
import com.hn.nutricarebe.entity.User;
import com.hn.nutricarebe.enums.*;
import com.hn.nutricarebe.exception.AppException;
import com.hn.nutricarebe.exception.ErrorCode;
import com.hn.nutricarebe.helper.GoogleLoginHelper;
import com.hn.nutricarebe.mapper.UserMapper;
import com.hn.nutricarebe.service.*;
import com.hn.nutricarebe.utils.PkceStore;
import com.hn.nutricarebe.utils.PkceUtil;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
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
    UserMapper userMapper;
    UserService userService;
    PkceStore pkceStore;
    WebClient webClient;

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

    @Override
    @Transactional
    public OnboardingResponse onBoarding(OnboardingRequest request) {
        //B1: Lưu user
        User savedUser = userService.saveOnboarding(request.getUser());
        UserCreationResponse userCreationResponse = userMapper.toUserCreationResponse(savedUser);
        //B2: Lưu profile
        ProfileCreationResponse profileCreationResponse = profileService.save(request.getProfile(), savedUser);
        //B3: Lưu bệnh nền
        Set<UUID> conditionIds = request.getConditions();
        List<UserConditionResponse> listCondition = new ArrayList<>();
        if(conditionIds != null && !conditionIds.isEmpty()){
            listCondition = userConditionService.saveUserCondition(UserConditionCreationRequest.builder()
                    .user(savedUser)
                    .conditionIds(conditionIds)
                    .build());
        }
        //B4: Lưu dị ứng
        Set<UUID> allergyIds = request.getAllergies();
        List<UserAllergyResponse> listAllergy = new ArrayList<>();
        if(allergyIds != null && !allergyIds.isEmpty()){
             UserAllergyCreationRequest uar = UserAllergyCreationRequest.builder()
                    .user(savedUser)
                    .allergyIds(allergyIds)
                    .build();
            listAllergy = userAllergyService.saveUserAllergy(uar);
        }
        //B5: Lập kế hoạch tuần (MealPlanDay - 7 ngày)
        MealPlanResponse mealPlanResponse = mealPlanDayService.createPlan(
                MealPlanCreationRequest.builder()
                        .userId(savedUser.getId())
                        .profile(request.getProfile())
                        .build(), 7
        );
        //B6: Lập kế hoạch chi tiết (MealPlanItem)

        //Trả về
        return OnboardingResponse.builder()
                .user(userCreationResponse)
                .token(generateToken(savedUser))
                .profile(profileCreationResponse)
                .conditions(listCondition)
                .allergies(listAllergy)
                .mealPlan(mealPlanResponse)
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
        UserCreationResponse saved = userService.saveGG(user);

        return LoginProviderResponse.builder()
                .user(saved)
                .token(generateToken(user))
                .isNewUser(isNewUser)
                .name(gp.getName())
                .urlAvatar(gp.getAvatar())
                .build();
    }

    private String generateToken(User user){
        JWSHeader header = new JWSHeader(JWSAlgorithm.HS512);

        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject(user.getId().toString())
                .issuer("nutricare.com")
                .issueTime(new Date())
                .expirationTime(new Date(Instant.now().plus(1, ChronoUnit.HOURS).toEpochMilli()))
                .claim("scope", user.getRole().toString())
                .build();

        Payload payload = new Payload(claimsSet.toJSONObject());

        JWSObject jwsObject = new JWSObject(header, payload);

        try {
            jwsObject.sign(new MACSigner(SIGNER_KEY.getBytes()));
            return jwsObject.serialize();
        } catch (JOSEException e) {
            log.error("Error while signing the token: {}", e.getMessage());
            throw new RuntimeException("Error while signing the token: " + e.getMessage());
        }
    }
}
