package com.beautycare.warehouse.service;

import com.beautycare.warehouse.model.Product;
import com.beautycare.warehouse.model.ProductStatus;
import com.beautycare.warehouse.model.SkinTypeTag;
import com.beautycare.warehouse.repository.ProductRepository;

import java.util.List;
import java.util.Optional;

public class PublishingGatekeeper {
    private final ProductRepository productRepository;

    public PublishingGatekeeper(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public static class PublishingResponse {
        private final boolean success;
        private final String message;
        private final Product product;

        public PublishingResponse(boolean success, String message, Product product) {
            this.success = success;
            this.message = message;
            this.product = product;
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public Product getProduct() { return product; }
    }

    public PublishingResponse requestPublish(String productName) {
        Optional<Product> productOpt = productRepository.findByName(productName);
        if (!productOpt.isPresent()) {
            return new PublishingResponse(false, "Không tìm thấy sản phẩm trong kho", null);
        }

        Product product = productOpt.get();

        // Chốt chặn: Phải có ít nhất một nhãn loại da xác định
        if (product.getSkinTypes().isEmpty()) {
            product.setStatus(ProductStatus.DRAFT);
            product.setVisibleOnStorefront(false);
            productRepository.save(product);
            return new PublishingResponse(
                    false,
                    "Sản phẩm phải có ít nhất một nhãn loại da xác định trước khi mở bán",
                    product
            );
        }

        product.setStatus(ProductStatus.PUBLISHED);
        product.setVisibleOnStorefront(true);
        productRepository.save(product);

        return new PublishingResponse(
                true,
                "Xuất bản sản phẩm thành công và đủ điều kiện phân loại da",
                product
        );
    }

    public void handleProductSkinTypesRemoved(Product product) {
        // Edge Case: Thu hồi sản phẩm đang bán nếu Admin vào sửa và xóa sạch nhãn da
        if (product.getSkinTypes().isEmpty()) {
            product.setStatus(ProductStatus.HIDDEN);
            product.setVisibleOnStorefront(false);
            product.addAuditLog("Sản phẩm bị tạm ẩn do thiếu phân loại nhãn da");
            productRepository.save(product);
        }
    }

    public List<Product> searchProductsBySkinType(SkinTypeTag skinTypeTag) {
        return productRepository.findBySkinTypeFilter(skinTypeTag);
    }
}
