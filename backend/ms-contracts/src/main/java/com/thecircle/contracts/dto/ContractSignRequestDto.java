package com.thecircle.contracts.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class ContractSignRequestDto {
    @NotNull(message = "validation.contract.required")
    @Valid
    private ContractDto contract;

    @Size(max = 20, message = "validation.signatureMode.size")
    private String signatureMode; // VISUAL or CRYPTO

    @Valid
    private VisualSignatureDto visualOptions;

    public ContractDto getContract() { return contract; }
    public void setContract(ContractDto contract) { this.contract = contract; }
    public String getSignatureMode() { return signatureMode; }
    public void setSignatureMode(String signatureMode) { this.signatureMode = signatureMode; }
    public VisualSignatureDto getVisualOptions() { return visualOptions; }
    public void setVisualOptions(VisualSignatureDto visualOptions) { this.visualOptions = visualOptions; }
}

