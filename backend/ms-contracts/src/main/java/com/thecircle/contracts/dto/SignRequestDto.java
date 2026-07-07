package com.thecircle.contracts.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class SignRequestDto {

    @NotBlank(message = "validation.signerEmail.required")
    @Email(message = "validation.signerEmail.invalid")
    @Size(max = 320, message = "validation.signerEmail.size")
    private String signerEmail;

    @Size(max = 20, message = "validation.signatureMode.size")
    private String signatureMode;

    private SignerRole signerRole;

    @NotNull(message = "validation.contract.required")
    @Valid
    private ContractDto contract;

    @Valid
    private VisualSignatureDto visualOptions;

    public String getSignerEmail() { return signerEmail; }
    public void setSignerEmail(String signerEmail) { this.signerEmail = signerEmail; }
    public SignerRole getSignerRole() { return signerRole; }
    public void setSignerRole(SignerRole signerRole) { this.signerRole = signerRole; }
    public String getSignatureMode() { return signatureMode; }
    public void setSignatureMode(String signatureMode) { this.signatureMode = signatureMode; }
    public ContractDto getContract() { return contract; }
    public void setContract(ContractDto contract) { this.contract = contract; }
    public VisualSignatureDto getVisualOptions() { return visualOptions; }
    public void setVisualOptions(VisualSignatureDto visualOptions) { this.visualOptions = visualOptions; }
}
