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
public class RegisterRequest {
    private String firstName;
    private String lastName;
    private String email;
    // Kept out of toString so accidental log.info("req={}", req) never leaks the
    // plaintext password to stdout, log files or SIEMs. Spring still binds the
    // value through the setter for normal deserialization.
    @ToString.Exclude
    private String password;
    private String address;
    private String idNumber;
}
