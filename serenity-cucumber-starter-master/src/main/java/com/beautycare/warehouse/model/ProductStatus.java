package com.beautycare.warehouse.model;

public enum ProductStatus {
    DRAFT("Bản nháp"),
    PUBLISHED("Đang mở bán"),
    HIDDEN("Tạm ẩn");

    private final String vietnameseName;

    ProductStatus(String vietnameseName) {
        this.vietnameseName = vietnameseName;
    }

    public String getVietnameseName() {
        return vietnameseName;
    }

    public static ProductStatus fromVietnamese(String text) {
        if (text == null) return null;
        String trimmed = text.trim();
        for (ProductStatus status : values()) {
            if (status.vietnameseName.equalsIgnoreCase(trimmed)) {
                return status;
            }
        }
        return valueOf(trimmed.toUpperCase());
    }
}
