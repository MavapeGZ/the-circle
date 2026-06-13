package com.thecircle.users.dto;

public record UserProfileDto(
    Long id,
    String email,
    String firstName,
    String lastName,
    String zone,
    String kycStatus
) {}

