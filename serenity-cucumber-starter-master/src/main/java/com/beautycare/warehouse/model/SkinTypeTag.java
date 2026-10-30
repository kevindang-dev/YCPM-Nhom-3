package com.beautycare.warehouse.model;

import java.util.Objects;

public class SkinTypeTag {
    public static final SkinTypeTag DA_DAU = new SkinTypeTag("Da dầu");
    public static final SkinTypeTag DA_KHO = new SkinTypeTag("Da khô");
    public static final SkinTypeTag DA_HON_HOP = new SkinTypeTag("Da hỗn hợp");
    public static final SkinTypeTag DA_NHAY_CAM = new SkinTypeTag("Da nhạy cảm");
    public static final SkinTypeTag MOI_LOAI_DA = new SkinTypeTag("Mọi loại da");
    public static final SkinTypeTag DA_MUN = new SkinTypeTag("Da mụn");

    private final String value;

    public SkinTypeTag(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Nhãn loại da không được để trống");
        }
        this.value = value.trim();
    }

    public String getValue() {
        return value;
    }

    public boolean isAllSkinTypes() {
        return "Mọi loại da".equalsIgnoreCase(value);
    }

    public static SkinTypeTag of(String text) {
        if (text == null) return null;
        String trimmed = text.trim();
        if ("Da dầu".equalsIgnoreCase(trimmed)) return DA_DAU;
        if ("Da khô".equalsIgnoreCase(trimmed)) return DA_KHO;
        if ("Da hỗn hợp".equalsIgnoreCase(trimmed)) return DA_HON_HOP;
        if ("Da nhạy cảm".equalsIgnoreCase(trimmed)) return DA_NHAY_CAM;
        if ("Mọi loại da".equalsIgnoreCase(trimmed)) return MOI_LOAI_DA;
        if ("Da mụn".equalsIgnoreCase(trimmed)) return DA_MUN;
        return new SkinTypeTag(trimmed);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SkinTypeTag that = (SkinTypeTag) o;
        return value.equalsIgnoreCase(that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value.toLowerCase());
    }

    @Override
    public String toString() {
        return value;
    }
}
