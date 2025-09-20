package com.hn.nutricarebe.mapper;

import com.hn.nutricarebe.dto.request.ProfileCreationRequest;
import com.hn.nutricarebe.dto.response.ProfileCreationResponse;
import com.hn.nutricarebe.entity.Profile;
import com.hn.nutricarebe.repository.ProfileRepository;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface ProfileMapper {
    public Profile toProfile(ProfileCreationRequest request);

    public ProfileCreationResponse toProfileCreationResponse(Profile profile);
}
