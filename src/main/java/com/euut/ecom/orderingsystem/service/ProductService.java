package com.euut.ecom.orderingsystem.service;

import com.euut.ecom.orderingsystem.dto.OrderItemRequestDto;
import com.euut.ecom.orderingsystem.dto.ProductResponseDto;
import com.euut.ecom.orderingsystem.entity.Product;
import com.euut.ecom.orderingsystem.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final DefaultRedisScript<Long> batchStockDecrementScript;
    private final DefaultRedisScript<Long> batchStockRestoreScript;

    private static final String STOCK_PREFIX = "product:stock:";

    public Product saveProduct(Product product) {
        Product saved = productRepository.save(product);
        redisTemplate.opsForValue().set(STOCK_PREFIX + saved.getSku(), String.valueOf(saved.getAvailableStock()));
        return saved;
    }

    @Cacheable(value = "products", key = "#sku")
    public ProductResponseDto getProductBySku(String sku) {
        Product product = productRepository.findBySku(sku)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + sku));
        return new ProductResponseDto(product.getSku(), product.getPrice(), product.getAvailableStock());
    }

//    public boolean deductStock(String sku, int quantity) {
//        return deductBatchStock(List.of(new OrderItemRequestDto(sku, quantity)));
//    }
//
//    public void restoreStock(String sku, int quantity) {
//        restoreBatchStock(List.of(new OrderItemRequestDto(sku, quantity)));
//    }

    public boolean deductBatchStock(List<OrderItemRequestDto> items) {
        List<OrderItemRequestDto> normalized = normalizeItems(items);

        List<String> keys = normalized.stream()
                .map(item -> STOCK_PREFIX + item.sku())
                .toList();

        Object[] args = normalized.stream()
                .map(item -> String.valueOf(item.quantity()))
                .toArray();

        Long result = redisTemplate.execute(batchStockDecrementScript, keys, args);
        return result != null && result == 1;
    }

    public void restoreBatchStock(List<OrderItemRequestDto> items) {
        List<OrderItemRequestDto> normalized = normalizeItems(items);

        List<String> keys = normalized.stream()
                .map(item -> STOCK_PREFIX + item.sku())
                .toList();

        Object[] args = normalized.stream()
                .map(item -> String.valueOf(item.quantity()))
                .toArray();

        redisTemplate.execute(batchStockRestoreScript, keys, args);
    }

    public List<OrderItemRequestDto> normalizeItems(List<OrderItemRequestDto> items) {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Order items cannot be empty");
        }

        for (OrderItemRequestDto item : items) {
            if (item == null || item.sku() == null || item.sku().isBlank() || item.quantity() <= 0) {
                throw new IllegalArgumentException("Invalid order item found in batch");
            }
        }

        // Merge duplicates into a LinkedHashMap to preserve predictable ordering
        Map<String, Integer> quantities = items.stream()
                .collect(Collectors.toMap(
                        OrderItemRequestDto::sku,
                        OrderItemRequestDto::quantity,
                        Math::addExact,
                        LinkedHashMap::new
                ));

        return quantities.entrySet().stream()
                .map(entry -> new OrderItemRequestDto(entry.getKey(), entry.getValue()))
                .toList();
    }
}