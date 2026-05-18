package com.thecircle.contracts.dto;

public class SignRequestDto {

    private String signerEmail;
    private String signatureMode;
    private ContractDto contract;
    private VisualSignatureDto visualOptions;

    public String getSignerEmail() { return signerEmail; }
    public void setSignerEmail(String signerEmail) { this.signerEmail = signerEmail; }
    public String getSignatureMode() { return signatureMode; }
    public void setSignatureMode(String signatureMode) { this.signatureMode = signatureMode; }
    public ContractDto getContract() { return contract; }
    public void setContract(ContractDto contract) { this.contract = contract; }
    public VisualSignatureDto getVisualOptions() { return visualOptions; }
    public void setVisualOptions(VisualSignatureDto visualOptions) { this.visualOptions = visualOptions; }
}
