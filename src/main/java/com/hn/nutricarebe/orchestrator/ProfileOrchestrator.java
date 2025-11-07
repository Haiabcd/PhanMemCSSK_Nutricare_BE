package com.hn.nutricarebe.orchestrator;

import java.util.UUID;

import com.hn.nutricarebe.dto.request.ProfileCreationRequest;

public interface ProfileOrchestrator {

    ProfileCreationRequest getByUserId_request(UUID userId);
}
