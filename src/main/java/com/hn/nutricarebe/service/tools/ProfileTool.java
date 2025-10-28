package com.hn.nutricarebe.service.tools;

import com.hn.nutricarebe.dto.response.ProfileCreationResponse;
import com.hn.nutricarebe.exception.AppException;
import com.hn.nutricarebe.exception.ErrorCode;
import com.hn.nutricarebe.service.ProfileService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ProfileTool {
    ProfileService profileService;
    private static final UUID DEFAULT_USER_ID = UUID.fromString("6b2df221-c835-47b7-a527-43c40acbd0df");

    @Tool(
            name = "getProfileSummary",
            description = "Lấy hồ sơ dinh dưỡng tóm tắt của người dùng hiện tại (dựa trên token)."
    )
    public ProfileCreationResponse get() {
//        var auth = SecurityContextHolder.getContext().getAuthentication();
//        if (auth == null || !auth.isAuthenticated()) {
//            throw new AppException(ErrorCode.UNAUTHORIZED);
//        }
//        UUID userId = UUID.fromString(auth.getName());
        return profileService.findByUserId(DEFAULT_USER_ID);

    }
}
