package com.hn.nutricarebe.mapper;


import com.hn.nutricarebe.dto.request.AllergyCreationRequest;
import com.hn.nutricarebe.dto.response.AllergyResponse;
import com.hn.nutricarebe.entity.Allergy;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface  AllergyMapper {
    Allergy toAllergy(AllergyCreationRequest request);
    AllergyResponse toAllergyResponse(Allergy allergy);
}
