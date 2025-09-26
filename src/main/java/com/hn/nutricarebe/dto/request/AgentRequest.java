package com.hn.nutricarebe.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AgentRequest {
   String userId;
   @NotBlank(message = "Tin nhắn không được để trống")
   String message;
}
