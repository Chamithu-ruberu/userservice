package com.greensphere.userservice.dto.request.tokenRequest;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
public class TokenRequest {
    private String username;
    private String role;
    @Builder.Default
    LocalDateTime now = LocalDateTime.now();
}
