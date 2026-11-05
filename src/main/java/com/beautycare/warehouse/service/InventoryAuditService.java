package com.beautycare.warehouse.service;

import com.beautycare.warehouse.model.Product;
import com.beautycare.warehouse.model.SkinTypeTag;
import com.beautycare.warehouse.repository.ProductRepository;

import java.util.*;

public class InventoryAuditService {
    private final ProductRepository productRepository;
    private final List<String> auditLogs = new ArrayList<>();

    public InventoryAuditService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public static class AuditReportRow {
        private final String sku;
        private final String productName;
        private final String distributor;
        private final String errorDetails;
        private final String lastImportDate;

        public AuditReportRow(String sku, String productName, String distributor, String errorDetails, String lastImportDate) {
            this.sku = sku;
            this.productName = productName;
            this.distributor = distributor;
            this.errorDetails = errorDetails;
            this.lastImportDate = lastImportDate;
        }

        public String getSku() { return sku; }
        public String getProductName() { return productName; }
        public String getDistributor() { return distributor; }
        public String getErrorDetails() { return errorDetails; }
        public String getLastImportDate() { return lastImportDate; }
    }

    public static class AuditReport {
        private final String fileName;
        private final String format;
        private final List<AuditReportRow> rows;

        public AuditReport(String fileName, String format, List<AuditReportRow> rows) {
            this.fileName = fileName;
            this.format = format;
            this.rows = rows;
        }

        public String getFileName() { return fileName; }
        public String getFormat() { return format; }
        public List<AuditReportRow> getRows() { return rows; }
    }

    public List<Product> filterByAuditCriterion(String criterion) {
        if ("Chưa phân loại da".equalsIgnoreCase(criterion.trim())) {
            return productRepository.findUnclassifiedSkinType();
        }
        return productRepository.findAll();
    }

    public void quickEditSkinTypes(String productName, Set<SkinTypeTag> newTags) {
        Optional<Product> productOpt = productRepository.findByName(productName);
        if (productOpt.isPresent()) {
            Product product = productOpt.get();
            product.addSkinTypes(newTags);
            product.setAuditError(null);
            productRepository.save(product);
        }
    }

    public AuditReport exportAuditReport(String format) {
        List<Product> unclassified = productRepository.findUnclassifiedSkinType();
        List<AuditReportRow> rows = new ArrayList<>();

        for (Product product : unclassified) {
            rows.add(new AuditReportRow(
                    product.getSku() != null ? product.getSku() : "SKU-" + product.getId().substring(0, 5),
                    product.getName(),
                    product.getDistributor() != null ? product.getDistributor() : "NPP Phân Phối Mỹ Phẩm",
                    product.getAuditError() != null ? product.getAuditError() : "Chưa phân loại da",
                    product.getLastImportDate() != null ? product.getLastImportDate() : "2026-10-20"
            ));
        }

        String fileName = "Audit_Report_Inventory_Data.xlsx";
        auditLogs.add("Xuất báo cáo kiểm toán thành công: " + fileName + " (" + format + ")");
        return new AuditReport(fileName, format, rows);
    }

    public List<String> getAuditLogs() {
        return Collections.unmodifiableList(auditLogs);
    }
}
