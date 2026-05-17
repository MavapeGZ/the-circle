package com.thecircle.contracts.dto;

public class ContractSignRequestDto {
    private ContractDto contract;
    private String signatureMode; // VISUAL or CRYPTO
    private VisualSignatureDto visualOptions;

    public ContractDto getContract() { return contract; }
    public void setContract(ContractDto contract) { this.contract = contract; }
    public String getSignatureMode() { return signatureMode; }
    public void setSignatureMode(String signatureMode) { this.signatureMode = signatureMode; }
    public VisualSignatureDto getVisualOptions() { return visualOptions; }
    public void setVisualOptions(VisualSignatureDto visualOptions) { this.visualOptions = visualOptions; }
}

