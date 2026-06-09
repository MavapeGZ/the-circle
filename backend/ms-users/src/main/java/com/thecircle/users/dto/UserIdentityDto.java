package com.thecircle.users.dto;

/**
 * Full identity projection for service-to-service use only (e.g. ms-contracts
 * building a signed contract PDF). Includes PII (address, ID number) that the
 * public {@link UserProfileDto} must never expose.
 */
public record UserIdentityDto(
    Long id,
    String email,
    String firstName,
    String lastName,
    String address,
    String idNumber
) {}
