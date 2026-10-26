package com.euut.ecom.orderingsystem.entity;

import java.math.BigDecimal;

public record CartItem(String sku, Integer quantity, BigDecimal unitPrice) {}