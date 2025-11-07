package com.hn.nutricarebe.dto.response;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class SupabaseIdentity {
    String id;
    String provider;
    String provider_id;
    String email;
    Boolean email_verified;
}
