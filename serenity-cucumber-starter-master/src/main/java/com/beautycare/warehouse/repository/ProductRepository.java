package com.beautycare.warehouse.repository;

import com.beautycare.warehouse.model.Product;
import com.beautycare.warehouse.model.ProductStatus;
import com.beautycare.warehouse.model.SkinTypeTag;

import java.util.List;
import java.util.Optional;

public interface ProductRepository {
    Product save(Product product);
    Optional<Product> findById(String id);
    Optional<Product> findByName(String name);
    List<Product> findAll();
    List<Product> findByStatus(ProductStatus status);
    List<Product> findBySkinTypeFilter(SkinTypeTag skinTypeTag);
    List<Product> findUnclassifiedSkinType();
    void deleteById(String id);
    void clear();
}
