package com.thecircle.users.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AuthenticationRequest {
    private String email;
    // Kept out of toString so accidental log.info("req={}", req) never leaks the
    // plaintext password. Spring's binding still goes through the setter normally.
    @ToString.Exclude
    private String password;
}
