package com.euut.ecom.orderingsystem.dto;

import java.util.List;

public record OrderEventDto(
        String orderId,
        Long userId,
        List<OrderItemRequestDto> items
) {}