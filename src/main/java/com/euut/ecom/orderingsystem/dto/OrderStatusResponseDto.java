package com.euut.ecom.orderingsystem.dto;

import com.euut.ecom.orderingsystem.entity.CartItem;
import com.euut.ecom.orderingsystem.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderStatusResponseDto(
        String orderId,
        OrderStatus status,
        BigDecimal totalAmount,
        List<CartItem> items,
        LocalDateTime createdAt
) {}