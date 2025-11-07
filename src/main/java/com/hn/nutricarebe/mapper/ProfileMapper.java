package com.hn.nutricarebe.mapper;

import org.mapstruct.Mapper;

import com.hn.nutricarebe.dto.request.ProfileCreationRequest;
import com.hn.nutricarebe.dto.response.ProfileCreationResponse;
import com.hn.nutricarebe.entity.Profile;

@Mapper(componentModel = "spring")
public interface ProfileMapper {
    Profile toProfile(ProfileCreationRequest request);

    ProfileCreationResponse toProfileCreationResponse(Profile profile);

    ProfileCreationRequest toProfileCreationRequest(Profile profile);
}
