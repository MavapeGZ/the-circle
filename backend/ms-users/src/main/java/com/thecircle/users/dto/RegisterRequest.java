package com.thecircle.users.dto;

import com.thecircle.users.validation.ValidationPatterns;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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

    @NotBlank(message = "validation.firstName.required")
    @Pattern(regexp = ValidationPatterns.NAME, message = "validation.firstName.pattern")
    private String firstName;

    @NotBlank(message = "validation.lastName.required")
    @Pattern(regexp = ValidationPatterns.NAME, message = "validation.lastName.pattern")
    private String lastName;

    @NotBlank(message = "validation.email.required")
    @Email(message = "validation.email.invalid")
    @Size(max = 320, message = "validation.email.size")
    private String email;

    // Kept out of toString so accidental log.info("req={}", req) never leaks the
    // plaintext password to stdout, log files or SIEMs. Spring still binds the
    // value through the setter for normal deserialization.
    @ToString.Exclude
    @NotBlank(message = "validation.password.required")
    @Pattern(regexp = ValidationPatterns.PASSWORD, message = "validation.password.pattern")
    private String password;

    // Optional at registration; only structural limits are enforced here.
    @Size(max = 255, message = "validation.address.size")
    @Pattern(regexp = ValidationPatterns.NO_ANGLE, message = "validation.address.noAngle")
    private String address;

    @Size(max = 50, message = "validation.idNumber.size")
    @Pattern(regexp = ValidationPatterns.NO_ANGLE, message = "validation.idNumber.noAngle")
    private String idNumber;
}
