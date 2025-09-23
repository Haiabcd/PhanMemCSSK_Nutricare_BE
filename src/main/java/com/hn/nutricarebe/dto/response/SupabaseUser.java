package com.hn.nutricarebe.dto.response;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class SupabaseUser {
    private String id;
    private String email;
    private Map<String, Object> user_metadata; // name, full_name, picture, avatar_url ...
}
