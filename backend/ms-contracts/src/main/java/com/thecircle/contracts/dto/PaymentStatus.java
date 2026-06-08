package com.thecircle.contracts.dto;

/**
 * Lifecycle of a simulated payment. The flow is buyer → escrow → (counterparty
 * signs ? released : refunded). FAILED is terminal and is only used when the
 * card validation refuses up front (e.g. demo card ending in 0000).
 */
public enum PaymentStatus {
    PENDING,
    ESCROWED,
    RELEASED,
    REFUNDED,
    FAILED
}
