package com.thecircle.contracts.dto;

import java.time.LocalDate;

public class SignerDto {
    private String fullName;
    private String idNumber;
    private String email;
    private String address;
    private String phone;
    private String signatureImageBase64; // optional
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

