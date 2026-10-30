package com.beautycare.warehouse.repository;

import com.beautycare.warehouse.model.Product;
import com.beautycare.warehouse.model.ProductStatus;
import com.beautycare.warehouse.model.SkinTypeTag;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class InMemoryProductRepository implements ProductRepository {
    private final Map<String, Product> database = new ConcurrentHashMap<>();

    @Override
    public Product save(Product product) {
        if (product.getId() == null || product.getId().trim().isEmpty()) {
            product.setId(UUID.randomUUID().toString());
        }
        database.put(product.getId(), product);
        return product;
    }

    @Override
    public Optional<Product> findById(String id) {
        return Optional.ofNullable(database.get(id));
    }

    @Override
    public Optional<Product> findByName(String name) {
        return database.values().stream()
                .filter(p -> p.getName() != null && p.getName().equalsIgnoreCase(name.trim()))
                .findFirst();
    }

    @Override
    public List<Product> findAll() {
        return new ArrayList<>(database.values());
    }

    @Override
    public List<Product> findByStatus(ProductStatus status) {
        return database.values().stream()
                .filter(p -> p.getStatus() == status)
                .collect(Collectors.toList());
    }

    @Override
    public List<Product> findBySkinTypeFilter(SkinTypeTag skinTypeTag) {
        return database.values().stream()
                .filter(p -> p.getStatus() == ProductStatus.PUBLISHED)
                .filter(p -> p.matchesSkinTypeFilter(skinTypeTag))
                .collect(Collectors.toList());
    }

    @Override
    public List<Product> findUnclassifiedSkinType() {
        return database.values().stream()
                .filter(p -> p.getSkinTypes().isEmpty())
                .collect(Collectors.toList());
    }

    @Override
    public void deleteById(String id) {
        database.remove(id);
    }

    @Override
    public void clear() {
        database.clear();
    }
}
