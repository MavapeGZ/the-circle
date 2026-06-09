package com.thecircle.contracts.dto;

/**
 * Lifecycle of a contract's security deposit (fianza).
 * NONE → DEPOSITED → (RELEASED | CLAIMED).
 */
public enum GuaranteeStatus {
    NONE,
    DEPOSITED,
    RELEASED,
    CLAIMED
}
