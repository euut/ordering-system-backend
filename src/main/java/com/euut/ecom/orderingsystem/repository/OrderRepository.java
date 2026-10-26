package com.euut.ecom.orderingsystem.repository;

import com.euut.ecom.orderingsystem.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    boolean existsByOrderId(String orderId);

    Optional<Order> findByOrderIdAndUserId(String orderId, Long userId);
}