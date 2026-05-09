package com.thecircle.users.dto;

public record KycResponse(
    boolean success,
    String message,
    String jwt
) {}
