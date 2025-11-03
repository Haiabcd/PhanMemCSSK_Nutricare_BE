package com.hn.nutricarebe.mapper;

import com.hn.nutricarebe.dto.request.TagDto;
import com.hn.nutricarebe.entity.Tag;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface TagMapper {
    TagDto toTagDto(Tag tag);
}
