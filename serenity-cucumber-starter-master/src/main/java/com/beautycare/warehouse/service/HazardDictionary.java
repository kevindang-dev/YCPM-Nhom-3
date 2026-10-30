package com.beautycare.warehouse.service;

import com.beautycare.warehouse.model.WarningFlag;

import java.util.*;

public class HazardDictionary {

    public static class HazardEntry {
        private final String category; // Nhóm chất (vd: Cồn khô, Chất bảo quản, Hương liệu)
        private final String inciName; // Tên hoạt chất INCI (vd: Alcohol Denat)
        private final String alias; // Tên đồng nghĩa / thương mại (vd: Denatured Alcohol)
        private final WarningFlag warningFlag; // Nội dung cờ cảnh báo (vd: Có chứa cồn khô)

        public HazardEntry(String category, String inciName, String alias, WarningFlag warningFlag) {
            this.category = category;
            this.inciName = inciName != null ? inciName.trim() : "";
            this.alias = alias != null ? alias.trim() : "";
            this.warningFlag = warningFlag;
        }

        public String getCategory() { return category; }
        public String getInciName() { return inciName; }
        public String getAlias() { return alias; }
        public WarningFlag getWarningFlag() { return warningFlag; }
    }

    private final List<HazardEntry> hazardEntries = new ArrayList<>();
    private final Set<String> safeFattyAlcohols = new HashSet<>(Arrays.asList(
            "cetearyl alcohol",
            "stearyl alcohol",
            "cetyl alcohol",
            "lauryl alcohol",
            "myristyl alcohol",
            "behenyl alcohol"
    ));

    public HazardDictionary() {
        loadDefaultDictionary();
    }

    public void loadDefaultDictionary() {
        hazardEntries.clear();
        addEntry("Cồn khô", "Alcohol Denat", "Denatured Alcohol", WarningFlag.CO_CHUA_CON_KHO);
        addEntry("Cồn khô", "Ethanol", "Ethyl Alcohol", WarningFlag.CO_CHUA_CON_KHO);
        addEntry("Chất bảo quản", "Methylparaben", "", WarningFlag.CHUA_PARABEN);
        addEntry("Chất bảo quản", "Propylparaben", "", WarningFlag.CHUA_PARABEN);
        addEntry("Hương liệu", "Fragrance", "Parfum", WarningFlag.CHUA_HUONG_LIEU);
    }

    public void clear() {
        hazardEntries.clear();
    }

    public void addEntry(String category, String inciName, String alias, WarningFlag flag) {
        hazardEntries.add(new HazardEntry(category, inciName, alias, flag));
    }

    public boolean isSafeFattyAlcohol(String ingredientName) {
        if (ingredientName == null) return false;
        return safeFattyAlcohols.contains(ingredientName.trim().toLowerCase());
    }

    public Optional<HazardEntry> findHazard(String ingredientName) {
        if (ingredientName == null) return Optional.empty();
        String cleaned = ingredientName.trim().toLowerCase();

        // Cồn béo dưỡng ẩm tuyệt đối không thuộc diện cảnh báo cồn khô
        if (isSafeFattyAlcohol(cleaned)) {
            return Optional.empty();
        }

        for (HazardEntry entry : hazardEntries) {
            if (!entry.getInciName().isEmpty() && entry.getInciName().equalsIgnoreCase(cleaned)) {
                return Optional.of(entry);
            }
            if (!entry.getAlias().isEmpty() && entry.getAlias().equalsIgnoreCase(cleaned)) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    public List<HazardEntry> getAllEntries() {
        return Collections.unmodifiableList(hazardEntries);
    }
}
