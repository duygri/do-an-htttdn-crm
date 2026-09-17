package com.htttdn.crm.controller;
import com.htttdn.crm.entity.Product; import com.htttdn.crm.repository.ProductRepository; import com.htttdn.crm.dto.admin.AdminDtos.*; import jakarta.validation.Valid; import org.springframework.data.domain.Page; import org.springframework.web.bind.annotation.*; import java.time.Instant;
@RestController @RequestMapping("/api/admin/products") public class AdminProductController extends AdminPageSupport { private final ProductRepository products; public AdminProductController(ProductRepository products){this.products=products;}
 @GetMapping public Page<Product> list(@RequestParam(defaultValue="")String q,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return products.findByNameContainingIgnoreCaseAndActiveTrue(q,page(page,size));}
 @PostMapping public Product create(@Valid @RequestBody ProductRequest r){Product p=new Product();copy(p,r);return products.save(p);}
 @PutMapping("/{id}") public Product update(@PathVariable Long id,@Valid @RequestBody ProductRequest r){Product p=products.findById(id).orElseThrow();copy(p,r);p.setUpdatedAt(Instant.now());return products.save(p);}
 @DeleteMapping("/{id}") public void archive(@PathVariable Long id){Product p=products.findById(id).orElseThrow();p.setActive(false);products.save(p);}
 @PatchMapping("/{id}/stock") public Product stock(@PathVariable Long id,@Valid @RequestBody StockRequest r){Product p=products.findById(id).orElseThrow();p.setStock(r.quantity());p.setUpdatedAt(Instant.now());return products.save(p);}
 private void copy(Product p,ProductRequest r){p.setName(r.name());p.setCategory(r.category());p.setDescription(r.description());p.setPrice(r.price());p.setStock(r.stock());p.setImageUrl(r.imageUrl());} }
