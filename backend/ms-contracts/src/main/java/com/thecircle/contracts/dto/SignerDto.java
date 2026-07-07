package com.thecircle.contracts.dto;

import com.thecircle.contracts.validation.ValidationPatterns;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public class SignerDto {
    @Size(max = 200, message = "validation.fullName.size")
    @Pattern(regexp = ValidationPatterns.NO_ANGLE, message = "validation.fullName.noAngle")
    private String fullName;

    @Size(max = 50, message = "validation.idNumber.size")
    @Pattern(regexp = ValidationPatterns.NO_ANGLE, message = "validation.idNumber.noAngle")
    private String idNumber;

    @Email(message = "validation.signerEmail.invalid")
    @Size(max = 320, message = "validation.signerEmail.size")
    private String email;

    @Size(max = 255, message = "validation.address.size")
    @Pattern(regexp = ValidationPatterns.NO_ANGLE, message = "validation.address.noAngle")
    private String address;

    @Size(max = 40, message = "validation.phone.size")
    @Pattern(regexp = ValidationPatterns.NO_ANGLE, message = "validation.phone.noAngle")
    private String phone;

    // optional; bounded so a giant base64 string cannot exhaust memory.
    @Size(max = ValidationPatterns.MAX_SIGNATURE_IMAGE_CHARS, message = "validation.signatureImage.size")
    private String signatureImageBase64;

    @Size(max = 200, message = "validation.signatureText.size")
    @Pattern(regexp = ValidationPatterns.NO_ANGLE, message = "validation.signatureText.noAngle")
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

