package com.euut.ecom.orderingsystem.repository;

import com.euut.ecom.orderingsystem.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySku(String sku);

    List<Product> findAllBySkuIn(List<String> skus);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE Product p SET p.availableStock = p.availableStock - :qty WHERE p.sku = :sku AND p.availableStock >= :qty")
    int deductStockAtomic(@Param("sku") String sku, @Param("qty") int qty);
}