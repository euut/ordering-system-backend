package com.euut.ecom.orderingsystem.controller;

import com.euut.ecom.orderingsystem.dto.ProductResponseDto;
import com.euut.ecom.orderingsystem.entity.Product;
import com.euut.ecom.orderingsystem.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @PostMapping
    public ResponseEntity<Product> createProduct(@RequestBody Product product) {
        return ResponseEntity.ok(productService.saveProduct(product));
    }

    @GetMapping("/{sku}")
    public ResponseEntity<ProductResponseDto> getProduct(@PathVariable String sku) {
        return ResponseEntity.ok(productService.getProductBySku(sku));
    }
}