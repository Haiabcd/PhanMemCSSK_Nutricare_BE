package com.hn.nutricarebe.service.impl;

import com.hn.nutricarebe.dto.request.MealPlanCreationRequest;
import com.hn.nutricarebe.dto.request.OnboardingRequest;
import com.hn.nutricarebe.dto.request.UserAllergyCreationRequest;
import com.hn.nutricarebe.dto.request.UserConditionCreationRequest;
import com.hn.nutricarebe.dto.response.*;
import com.hn.nutricarebe.entity.Profile;
import com.hn.nutricarebe.entity.User;
import com.hn.nutricarebe.enums.*;
import com.hn.nutricarebe.exception.AppException;
import com.hn.nutricarebe.exception.ErrorCode;
import com.hn.nutricarebe.helper.GoogleLoginHelper;
import com.hn.nutricarebe.mapper.ProfileMapper;
import com.hn.nutricarebe.mapper.UserMapper;
import com.hn.nutricarebe.repository.ProfileRepository;
import com.hn.nutricarebe.repository.UserRepository;
import com.hn.nutricarebe.service.AuthService;
import com.hn.nutricarebe.service.UserAllergyService;
import com.hn.nutricarebe.service.UserConditionService;
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
    UserRepository userRepository;
    ProfileRepository profileRepository;
    UserMapper userMapper;
    ProfileMapper profileMapper;
    UserAllergyService userAllergyService;
    MealPlanDayServiceImpl mealPlanDayServiceImpl;
    UserConditionService userConditionService;
    PkceStore pkceStore;
    WebClient webClient;;

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
        if(userRepository.existsByDeviceId(request.getUser().getDeviceId())){
            throw new AppException(ErrorCode.DEVICE_ID_EXISTED);
        }
        User user =  userMapper.toUser(request.getUser());
        user.setRole(Role.GUEST);
        user.setProvider(Provider.NONE);
        user.setStatus(UserStatus.ACTIVE);
        User savedUser = userRepository.save(user);
        UserCreationResponse userCreationResponse = userMapper.toUserCreationResponse(savedUser);
        //B2: Lưu profile
        Profile profile = profileMapper.toProfile(request.getProfile());
        profile.setUser(savedUser);
        Profile savedProfile = profileRepository.save(profile);
        ProfileCreationResponse profileCreationResponse = profileMapper.toProfileCreationResponse(savedProfile);
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
        MealPlanResponse mealPlanResponse = mealPlanDayServiceImpl.createPlan(
                MealPlanCreationRequest.builder()
                        .userId(savedUser.getId())
                        .profile(request.getProfile())
                        .build(), 7
        );
        //B6: Lập kế hoạch chi tiết (MealPlanItem)



        //Tạo token (xong)
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
//    public LoginProviderResponse googleCallback(String code, String state,String device) {
    public SupabaseUser googleCallback(String code, String state,String device) {

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

        SupabaseUser su = tokenRes.getUser();
        LoginProfile gp = GoogleLoginHelper.parse(su);

        if (gp.getProviderUserId().isBlank()) {
            throw new AppException(ErrorCode.TOKEN_EXCHANGE_FAILED);
        }

//        boolean isNewUser = false;
//
//        // 1) Kiểm tra đã liên kết gg chưa
//        User user = userRepository.findByProviderUserId(providerUserId).orElse(null);
//
//        // 2) Nếu vẫn chưa, thử theo device
//        if (user == null && device != null && !device.isBlank()) {
//            user = userRepository.findByDeviceId(device).orElse(null);
//        }
//
//        if (user == null) {
//            // Tạo mới user
//            user = User.builder()
//                    .deviceId((device != null && !device.isBlank()) ? device : null)
//                    .email((email != null && !email.isBlank()) ? email : null)
//                    .providerUserId(providerUserId)
//                    .provider(Provider.SUPABASE_GOOGLE)
//                    .role(Role.USER)
//                    .status(UserStatus.ACTIVE)
//                    .build();
//            isNewUser = true;
//        } else {
//            // Đã có user
//            if (user.getRole() == Role.GUEST) {
//                user.setRole(Role.USER);
//                user.setProviderUserId(providerUserId);
//                user.setProvider(Provider.SUPABASE_GOOGLE);
//                user.setStatus(UserStatus.ACTIVE);
//                if ((user.getDeviceId() == null || user.getDeviceId().isBlank()) && device != null && !device.isBlank()) {
//                    user.setDeviceId(device);
//                }
//
//                if (user.getEmail() == null || user.getEmail().isBlank() || user.getEmail().equalsIgnoreCase(email)) {
//                        user.setEmail(email);
//                }
//                Profile profile = profileRepository.findByUser_Id(user.getId())
//                        .orElseThrow(() -> new AppException(ErrorCode.PROFILE_NOT_FOUND));
//                profile.setName(name);
//                profile.setAvataUrl(avatar);
//            }
//        }
//
//        // Lưu một lần duy nhất
//        User saved = userRepository.save(user);

        // Trả token theo user đã lưu
//        LoginProviderResponse data = LoginProviderResponse.builder()
//                .user(saved)
//                .token(generateToken(saved))
//                .isNewUser(isNewUser)
//                .name(name)
//                .urlAvatar(avatar)
//                .build();

        return su;
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
