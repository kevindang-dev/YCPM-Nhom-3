package com.beautycare.warehouse.model;

import java.util.*;

public class Product {
    private String id;
    private String sku;
    private String name;
    private String distributor;
    private final Set<SkinTypeTag> skinTypes = new LinkedHashSet<>();
    private String rawInci;
    private final List<String> parsedIngredients = new ArrayList<>();
    private ProductStatus status = ProductStatus.DRAFT;
    private final Set<WarningFlag> warningFlags = new LinkedHashSet<>();
    private final Set<String> safetyLabels = new LinkedHashSet<>();
    private boolean isVisibleOnStorefront = false;
    private String auditError;
    private String lastImportDate;
    private final List<String> auditLogs = new ArrayList<>();

    public Product() {
        this.id = UUID.randomUUID().toString();
    }

    public Product(String name) {
        this.id = UUID.randomUUID().toString();
        this.name = name;
        this.status = ProductStatus.DRAFT;
    }

    public Product(String sku, String name, String distributor, String auditError, String lastImportDate) {
        this.id = UUID.randomUUID().toString();
        this.sku = sku;
        this.name = name;
        this.distributor = distributor;
        this.auditError = auditError;
        this.lastImportDate = lastImportDate;
        this.status = ProductStatus.DRAFT;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDistributor() { return distributor; }
    public void setDistributor(String distributor) { this.distributor = distributor; }

    public Set<SkinTypeTag> getSkinTypes() { return Collections.unmodifiableSet(skinTypes); }

    public void addSkinType(SkinTypeTag tag) {
        if (tag != null) {
            this.skinTypes.add(tag);
        }
    }

    public void addSkinTypes(Collection<SkinTypeTag> tags) {
        if (tags != null) {
            this.skinTypes.addAll(tags);
        }
    }

    public void removeSkinType(SkinTypeTag tag) {
        this.skinTypes.remove(tag);
    }

    public void clearSkinTypes() {
        this.skinTypes.clear();
    }

    public boolean hasSkinType(SkinTypeTag tag) {
        return this.skinTypes.contains(tag);
    }

    public boolean matchesSkinTypeFilter(SkinTypeTag filterTag) {
        if (filterTag == null) return true;
        // Nếu sản phẩm mang nhãn bao hàm "Mọi loại da", tự động kế thừa và khớp với mọi bộ lọc da đơn lẻ
        if (skinTypes.stream().anyMatch(SkinTypeTag::isAllSkinTypes)) {
            return true;
        }
        return skinTypes.contains(filterTag);
    }

    public String getRawInci() { return rawInci; }
    public void setRawInci(String rawInci) { this.rawInci = rawInci; }

    public List<String> getParsedIngredients() { return Collections.unmodifiableList(parsedIngredients); }
    public void setParsedIngredients(List<String> ingredients) {
        this.parsedIngredients.clear();
        if (ingredients != null) {
            this.parsedIngredients.addAll(ingredients);
        }
    }

    public ProductStatus getStatus() { return status; }
    public void setStatus(ProductStatus status) {
        this.status = status;
        if (status == ProductStatus.PUBLISHED) {
            this.isVisibleOnStorefront = true;
        } else {
            this.isVisibleOnStorefront = false;
        }
    }

    public Set<WarningFlag> getWarningFlags() { return Collections.unmodifiableSet(warningFlags); }
    public void addWarningFlag(WarningFlag flag) {
        if (flag != null) {
            this.warningFlags.add(flag);
        }
    }
    public boolean hasWarningFlag(WarningFlag flag) {
        return this.warningFlags.contains(flag);
    }
    public void clearWarningFlags() {
        this.warningFlags.clear();
    }

    public Set<String> getSafetyLabels() { return Collections.unmodifiableSet(safetyLabels); }
    public void addSafetyLabel(String label) {
        if (label != null) {
            this.safetyLabels.add(label);
        }
    }
    public boolean hasSafetyLabel(String label) {
        return this.safetyLabels.contains(label);
    }

    public boolean isVisibleOnStorefront() { return isVisibleOnStorefront; }
    public void setVisibleOnStorefront(boolean visibleOnStorefront) {
        this.isVisibleOnStorefront = visibleOnStorefront;
    }

    public String getAuditError() { return auditError; }
    public void setAuditError(String auditError) { this.auditError = auditError; }

    public String getLastImportDate() { return lastImportDate; }
    public void setLastImportDate(String lastImportDate) { this.lastImportDate = lastImportDate; }

    public List<String> getAuditLogs() { return Collections.unmodifiableList(auditLogs); }
    public void addAuditLog(String log) {
        this.auditLogs.add(log);
    }
}
