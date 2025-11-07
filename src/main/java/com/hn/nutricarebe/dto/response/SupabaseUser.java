package com.hn.nutricarebe.dto.response;

import java.util.List;
import java.util.Map;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class SupabaseUser {
    String id;
    String email;
    Map<String, Object> user_metadata;
    List<SupabaseIdentity> identities;
}
