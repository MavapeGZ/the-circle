package com.thecircle.contracts.dto;

import com.thecircle.contracts.validation.ValidationPatterns;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public class SignerDto {
    @Size(max = 200, message = "Full name must be at most 200 characters")
    @Pattern(regexp = ValidationPatterns.NO_ANGLE, message = "Full name " + ValidationPatterns.NO_ANGLE_MSG)
    private String fullName;

    @Size(max = 50, message = "ID number must be at most 50 characters")
    @Pattern(regexp = ValidationPatterns.NO_ANGLE, message = "ID number " + ValidationPatterns.NO_ANGLE_MSG)
    private String idNumber;

    @Email(message = "Signer email must be a valid address")
    @Size(max = 320, message = "Signer email must be at most 320 characters")
    private String email;

    @Size(max = 255, message = "Address must be at most 255 characters")
    @Pattern(regexp = ValidationPatterns.NO_ANGLE, message = "Address " + ValidationPatterns.NO_ANGLE_MSG)
    private String address;

    @Size(max = 40, message = "Phone must be at most 40 characters")
    @Pattern(regexp = ValidationPatterns.NO_ANGLE, message = "Phone " + ValidationPatterns.NO_ANGLE_MSG)
    private String phone;

    // optional; bounded so a giant base64 string cannot exhaust memory.
    @Size(max = ValidationPatterns.MAX_SIGNATURE_IMAGE_CHARS, message = "Signature image is too large")
    private String signatureImageBase64;

    @Size(max = 200, message = "Signature text must be at most 200 characters")
    @Pattern(regexp = ValidationPatterns.NO_ANGLE, message = "Signature text " + ValidationPatterns.NO_ANGLE_MSG)
    private String signatureText; // optional

    private LocalDate signingDate;

    // getters and setters
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public String getIdNumber() { return idNumber; }
    public void setIdNumber(String idNumber) { this.idNumber = idNumber; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getSignatureImageBase64() { return signatureImageBase64; }
    public void setSignatureImageBase64(String signatureImageBase64) { this.signatureImageBase64 = signatureImageBase64; }
    public String getSignatureText() { return signatureText; }
    public void setSignatureText(String signatureText) { this.signatureText = signatureText; }
    public LocalDate getSigningDate() { return signingDate; }
    public void setSigningDate(LocalDate signingDate) { this.signingDate = signingDate; }
}

