package com.euut.ecom.orderingsystem.dto;

import java.math.BigDecimal;

public record ProductResponseDto(
        String sku,
        BigDecimal price,
        Integer availableStock
) {}