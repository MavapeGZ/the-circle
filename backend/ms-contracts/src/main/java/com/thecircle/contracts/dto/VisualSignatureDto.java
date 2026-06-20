package com.thecircle.contracts.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;

import java.util.Map;

public class VisualSignatureDto {
    public static class SignaturePlacement {
        @Min(value = 0, message = "Signature page cannot be negative")
        private int page = 0;
        private float x = 50f;
        private float y = 100f;
        private float width = 150f;
        private float height = 50f;

        public int getPage() { return page; }
        public void setPage(int page) { this.page = page; }
        public float getX() { return x; }
        public void setX(float x) { this.x = x; }
        public float getY() { return y; }
        public void setY(float y) { this.y = y; }
        public float getWidth() { return width; }
        public void setWidth(float width) { this.width = width; }
        public float getHeight() { return height; }
        public void setHeight(float height) { this.height = height; }
    }

    private Map<String, @Valid SignaturePlacement> placements;

    public Map<String, SignaturePlacement> getPlacements() { return placements; }
    public void setPlacements(Map<String, SignaturePlacement> placements) { this.placements = placements; }
}

