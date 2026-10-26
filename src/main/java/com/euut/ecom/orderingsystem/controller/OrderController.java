package com.euut.ecom.orderingsystem.controller;

import com.euut.ecom.orderingsystem.dto.CreateOrderRequestDto;
import com.euut.ecom.orderingsystem.dto.OrderResponseDto;
import com.euut.ecom.orderingsystem.dto.OrderStatusResponseDto;
import com.euut.ecom.orderingsystem.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<OrderResponseDto> createOrder(@Valid @RequestBody CreateOrderRequestDto request, Authentication authentication) {
        return ResponseEntity.accepted().body(orderService.processOrder(authentication.getName(), request));
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderStatusResponseDto> getOrderStatus(Principal principal, @PathVariable String orderId) {
        return ResponseEntity.ok(orderService.getOrderStatus(orderId, principal.getName()));
    }
}