package com.hn.nutricarebe.mapper;

import org.mapstruct.Mapper;

import com.hn.nutricarebe.dto.request.TagDto;
import com.hn.nutricarebe.entity.Tag;

@Mapper(componentModel = "spring")
public interface TagMapper {
    TagDto toTagDto(Tag tag);
}
