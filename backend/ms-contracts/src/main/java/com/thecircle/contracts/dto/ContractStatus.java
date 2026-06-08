package com.thecircle.contracts.dto;

public enum ContractStatus {
    DRAFT,
    PENDING_SIGNATURES,
    // Buyer signed and paid (funds in escrow). Waiting for the counterparty to sign
    // within the escrow window so the funds can be released.
    AWAITING_COUNTERPARTY,
    ACTIVE,
    COMPLETED,
    CANCELLED
}
