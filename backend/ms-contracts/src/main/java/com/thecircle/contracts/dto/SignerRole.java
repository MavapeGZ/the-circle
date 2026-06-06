package com.thecircle.contracts.dto;

/**
 * Which party of a contract is signing. RECEIVER is the buyer/borrower who
 * initiates the deal; OWNER is the seller/lender who countersigns afterwards.
 */
public enum SignerRole {
    RECEIVER,
    OWNER
}
