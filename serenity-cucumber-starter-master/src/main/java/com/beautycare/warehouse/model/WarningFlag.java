package com.beautycare.warehouse.model;

import java.util.Objects;

public class WarningFlag {
    public static final WarningFlag CO_CHUA_CON_KHO = new WarningFlag("Có chứa cồn khô");
    public static final WarningFlag CHUA_PARABEN = new WarningFlag("Chứa Paraben");
    public static final WarningFlag CHUA_HUONG_LIEU = new WarningFlag("Chứa hương liệu");
    public static final WarningFlag KHONG_CHUA_CON_KHO = new WarningFlag("Không chứa cồn khô");

    private final String flagName;

    public WarningFlag(String flagName) {
        if (flagName == null || flagName.trim().isEmpty()) {
            throw new IllegalArgumentException("Tên cờ cảnh báo không được để trống");
        }
        this.flagName = flagName.trim();
    }

    public String getFlagName() {
        return flagName;
    }

    public static WarningFlag of(String name) {
        if (name == null) return null;
        String trimmed = name.trim();
        if ("Có chứa cồn khô".equalsIgnoreCase(trimmed)) return CO_CHUA_CON_KHO;
        if ("Chứa Paraben".equalsIgnoreCase(trimmed)) return CHUA_PARABEN;
        if ("Chứa hương liệu".equalsIgnoreCase(trimmed)) return CHUA_HUONG_LIEU;
        if ("Không chứa cồn khô".equalsIgnoreCase(trimmed)) return KHONG_CHUA_CON_KHO;
        return new WarningFlag(trimmed);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WarningFlag that = (WarningFlag) o;
        return flagName.equalsIgnoreCase(that.flagName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(flagName.toLowerCase());
    }

    @Override
    public String toString() {
        return flagName;
    }
}
