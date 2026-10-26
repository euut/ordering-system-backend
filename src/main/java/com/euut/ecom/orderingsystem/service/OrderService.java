package com.euut.ecom.orderingsystem.service;

import com.euut.ecom.orderingsystem.config.RabbitMQConfig;
import com.euut.ecom.orderingsystem.dto.*;
import com.euut.ecom.orderingsystem.entity.*;
import com.euut.ecom.orderingsystem.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final ProductService productService;
    private final RabbitTemplate rabbitTemplate;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    public OrderResponseDto processOrder(String userEmail, CreateOrderRequestDto request) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userEmail));

        // Normalize incoming items to merge duplicate SKUs and ensure order validation
        List<OrderItemRequestDto> normalizedItems = productService.normalizeItems(request.items());

        String orderId = UUID.randomUUID().toString();

        boolean success = productService.deductBatchStock(normalizedItems);
        if (!success) {
            throw new IllegalArgumentException("Insufficient stock for one or more items.");
        }

        try {
            OrderEventDto event = new OrderEventDto(orderId, user.getId(), normalizedItems);
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KEY, event);
            return new OrderResponseDto(orderId, "Order accepted and processing", OrderStatus.PENDING);

        } catch (Exception ex) {
            log.error("Failed to publish order event. Restoring Redis stock.", ex);
            productService.restoreBatchStock(normalizedItems);
            throw new IllegalStateException("Queue error, order aborted.", ex);
        }
    }

    @Transactional
    public void fulfillOrder(OrderEventDto event) {
        if (orderRepository.existsByOrderId(event.orderId())) {
            log.info("Order {} has already been fulfilled. Skipping duplicate processing.", event.orderId());
            return;
        }

        // Sort items by SKU alphabetically to prevent deadlocks
        List<OrderItemRequestDto> sortedItems = new ArrayList<>(event.items());
        sortedItems.sort(Comparator.comparing(OrderItemRequestDto::sku));

        // Fetch all products at once to eliminate N+1 database queries
        List<String> skus = sortedItems.stream().map(OrderItemRequestDto::sku).toList();
        List<Product> products = productRepository.findAllBySkuIn(skus);

        if (products.size() != sortedItems.size()) {
            // If this happens, database transaction will roll back
            // We must catch this in OrderConsumer to restore Redis stock
            throw new IllegalStateException("One or more products from the order are missing in the database.");
        }

        Map<String, Product> productMap = products.stream().collect(Collectors.toMap(Product::getSku, Function.identity()));

        List<CartItem> cartItems = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        for (OrderItemRequestDto itemDto : sortedItems) {
            int updatedRows = productRepository.deductStockAtomic(itemDto.sku(), itemDto.quantity());

            if (updatedRows == 0) {
                // If DB deduction fails because DB stock went negative, we throw an exception.
                throw new IllegalStateException("Database stock deduction failed for SKU: " + itemDto.sku());
            }

            Product product = productMap.get(itemDto.sku());
            BigDecimal itemTotal = product.getPrice().multiply(BigDecimal.valueOf(itemDto.quantity()));
            totalAmount = totalAmount.add(itemTotal);

            cartItems.add(new CartItem(product.getSku(), itemDto.quantity(), product.getPrice()));
        }

        Order order = new Order();
        order.setOrderId(event.orderId());
        order.setUserId(event.userId());
        order.setTotalAmount(totalAmount);
        order.setStatus(OrderStatus.COMPLETED);
        order.setItems(cartItems);

        orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public OrderStatusResponseDto getOrderStatus(String orderId, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userEmail));

        Order order = orderRepository.findByOrderIdAndUserId(orderId, user.getId())
                .orElseThrow(() -> new IllegalArgumentException("Order not found or access denied: " + orderId));

        return new OrderStatusResponseDto(
                order.getOrderId(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getItems(),
                order.getCreatedAt()
        );
    }
}
