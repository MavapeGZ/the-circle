package com.thecircle.contracts.dto;

public enum ContractStatus {
    DRAFT,
    PENDING_SIGNATURES,
    // Buyer signed and paid (funds in escrow). Waiting for the counterparty to sign
    // within the escrow window so the funds can be released.
    AWAITING_COUNTERPARTY,
    ACTIVE,
    // Both parties confirmed hand-over (owner delivered, receiver received). The
    // deal is fully done and a review can now be left. Reached from ACTIVE.
    DELIVERED,
    COMPLETED,
    CANCELLED
}
