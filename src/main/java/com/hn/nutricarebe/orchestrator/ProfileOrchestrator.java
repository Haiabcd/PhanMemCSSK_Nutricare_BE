package com.hn.nutricarebe.orchestrator;

import com.hn.nutricarebe.dto.request.ProfileCreationRequest;
import java.util.UUID;

public interface ProfileOrchestrator {

    ProfileCreationRequest getByUserId_request(UUID userId);
}
