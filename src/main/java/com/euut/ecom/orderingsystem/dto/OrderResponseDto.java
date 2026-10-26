package com.euut.ecom.orderingsystem.dto;

import com.euut.ecom.orderingsystem.entity.OrderStatus;

public record OrderResponseDto(
        String orderId,
        String message,
        OrderStatus status
) {}