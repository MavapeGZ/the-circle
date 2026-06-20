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

    @NotBlank(message = "First name is required")
    @Pattern(regexp = ValidationPatterns.NAME, message = "First name " + ValidationPatterns.NAME_MSG)
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Pattern(regexp = ValidationPatterns.NAME, message = "Last name " + ValidationPatterns.NAME_MSG)
    private String lastName;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid address")
    @Size(max = 320, message = "Email must be at most 320 characters")
    private String email;

    // Kept out of toString so accidental log.info("req={}", req) never leaks the
    // plaintext password to stdout, log files or SIEMs. Spring still binds the
    // value through the setter for normal deserialization.
    @ToString.Exclude
    @NotBlank(message = "Password is required")
    @Pattern(regexp = ValidationPatterns.PASSWORD, message = "Password " + ValidationPatterns.PASSWORD_MSG)
    private String password;

    // Optional at registration; only structural limits are enforced here.
    @Size(max = 255, message = "Address must be at most 255 characters")
    @Pattern(regexp = ValidationPatterns.NO_ANGLE, message = "Address " + ValidationPatterns.NO_ANGLE_MSG)
    private String address;

    @Size(max = 50, message = "ID number must be at most 50 characters")
    @Pattern(regexp = ValidationPatterns.NO_ANGLE, message = "ID number " + ValidationPatterns.NO_ANGLE_MSG)
    private String idNumber;
}
