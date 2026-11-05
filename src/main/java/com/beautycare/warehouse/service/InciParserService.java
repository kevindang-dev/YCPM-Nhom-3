package com.beautycare.warehouse.service;

import com.beautycare.warehouse.model.Product;
import com.beautycare.warehouse.model.WarningFlag;

import java.util.*;

public class InciParserService {
    private final HazardDictionary hazardDictionary;

    public InciParserService(HazardDictionary hazardDictionary) {
        this.hazardDictionary = hazardDictionary;
    }

    public static class ParseResult {
        private final List<String> ingredients = new ArrayList<>();
        private final Map<WarningFlag, String> detectedHazards = new LinkedHashMap<>();
        private final List<String> detectedSafeFattyAlcohols = new ArrayList<>();
        private boolean containsDryAlcohol = false;

        public List<String> getIngredients() { return ingredients; }
        public Map<WarningFlag, String> getDetectedHazards() { return detectedHazards; }
        public List<String> getDetectedSafeFattyAlcohols() { return detectedSafeFattyAlcohols; }
        public boolean isContainsDryAlcohol() { return containsDryAlcohol; }
        public boolean isAlcoholFree() { return !containsDryAlcohol; }
    }

    public ParseResult parseInci(String inciText) {
        ParseResult result = new ParseResult();
        if (inciText == null || inciText.trim().isEmpty()) {
            return result;
        }

        String[] tokens = inciText.split("[,\\n\\r]+");
        for (String rawToken : tokens) {
            String token = rawToken.trim();
            if (token.isEmpty()) continue;
            result.ingredients.add(token);

            // Kiểm tra cồn béo an toàn trước
            if (hazardDictionary.isSafeFattyAlcohol(token)) {
                result.detectedSafeFattyAlcohols.add(token);
                continue;
            }

            // Kiểm tra hoạt chất nhạy cảm
            Optional<HazardDictionary.HazardEntry> hazardOpt = hazardDictionary.findHazard(token);
            if (hazardOpt.isPresent()) {
                HazardDictionary.HazardEntry hazard = hazardOpt.get();
                result.detectedHazards.put(hazard.getWarningFlag(), token);
                if (hazard.getWarningFlag().equals(WarningFlag.CO_CHUA_CON_KHO)) {
                    result.containsDryAlcohol = true;
                }
            }
        }

        return result;
    }

    public ParseResult parseAndApplyToProduct(Product product, String inciText) {
        ParseResult result = parseInci(inciText);
        product.setRawInci(inciText);
        product.setParsedIngredients(result.getIngredients());

        for (WarningFlag flag : result.getDetectedHazards().keySet()) {
            product.addWarningFlag(flag);
        }

        if (!result.isContainsDryAlcohol() && !result.getDetectedSafeFattyAlcohols().isEmpty()) {
            product.addSafetyLabel("Không chứa cồn khô");
        }

        return result;
    }

    public boolean isSafeFattyAlcohol(String name) {
        return hazardDictionary.isSafeFattyAlcohol(name);
    }

    public Optional<HazardDictionary.HazardEntry> findHazard(String name) {
        return hazardDictionary.findHazard(name);
    }
}
