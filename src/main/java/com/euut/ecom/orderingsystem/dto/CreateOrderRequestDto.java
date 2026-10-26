package com.euut.ecom.orderingsystem.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record CreateOrderRequestDto(
        @NotEmpty(message = "Order must contain at least one item")
        List<OrderItemRequestDto> items
) {}