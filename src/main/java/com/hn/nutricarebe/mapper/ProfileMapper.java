package com.hn.nutricarebe.mapper;

import com.hn.nutricarebe.dto.request.ProfileCreationRequest;
import com.hn.nutricarebe.dto.response.ProfileCreationResponse;
import com.hn.nutricarebe.entity.Profile;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ProfileMapper {
    Profile toProfile(ProfileCreationRequest request);

    ProfileCreationResponse toProfileCreationResponse(Profile profile);

    ProfileCreationRequest toProfileCreationRequest(Profile profile);
}
