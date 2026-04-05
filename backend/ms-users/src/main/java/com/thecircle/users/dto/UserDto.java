package com.thecircle.users.dto;

public record UserDto(
    String id,
    String name,
    String location,
    Double averageRating
) {}
