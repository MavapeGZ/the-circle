package com.thecircle.contracts.dto;

/**
 * Minimal view of a gamification badge the signer just unlocked, surfaced in the
 * sign-confirm response so the UI can toast it. Mirrors the fields ms-gamification
 * returns in its award response.
 */
public class EarnedBadgeDto {

    private String code;
    private String name;
    private String iconUrl;

    public EarnedBadgeDto() {
    }

    public EarnedBadgeDto(String code, String name, String iconUrl) {
        this.code = code;
        this.name = name;
        this.iconUrl = iconUrl;
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getIconUrl() { return iconUrl; }
    public void setIconUrl(String iconUrl) { this.iconUrl = iconUrl; }
}
