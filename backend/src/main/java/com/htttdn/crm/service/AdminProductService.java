package com.htttdn.crm.service;

import com.htttdn.crm.dto.admin.AdminDtos.ProductRequest;
import com.htttdn.crm.dto.admin.AdminDtos.StockRequest;
import com.htttdn.crm.entity.Category;
import com.htttdn.crm.entity.Product;
import com.htttdn.crm.repository.CategoryRepository;
import com.htttdn.crm.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AdminProductService extends AdminServiceSupport {
    private final ProductRepository products;
    private final CategoryRepository categories;
    public AdminProductService(ProductRepository products, CategoryRepository categories) {
        this.products = products;
        this.categories = categories;
    }
    public Page<Product> list(String q, int page, int size) { return products.findByNameContainingIgnoreCaseAndActiveTrue(q, page(page, size)); }
    @Transactional public Product create(ProductRequest request) { Product product = new Product(); copy(product, request); return products.save(product); }
    @Transactional public Product update(Long id, ProductRequest request) { Product product = get(id); copy(product, request); product.setUpdatedAt(Instant.now()); return products.save(product); }
    @Transactional public void archive(Long id) { Product product = get(id); product.setActive(false); products.save(product); }
    @Transactional public Product stock(Long id, StockRequest request) { Product product = get(id); product.setStock(request.quantity()); product.setUpdatedAt(Instant.now()); return products.save(product); }
    private Product get(Long id) { return products.findById(id).orElseThrow(); }
    private void copy(Product p, ProductRequest r) {
        p.setName(r.name());
        if (r.category() != null && !r.category().isBlank()) {
            Category category = categories.findByNameIgnoreCase(r.category().trim())
                    .orElseGet(() -> {
                        Category created = new Category();
                        created.setName(r.category().trim());
                        return categories.save(created);
                    });
            p.setCategoryEntity(category);
        }
        p.setDescription(r.description());
        p.setPrice(r.price());
        p.setStock(r.stock());
        p.setImageUrl(r.imageUrl());
    }
}
