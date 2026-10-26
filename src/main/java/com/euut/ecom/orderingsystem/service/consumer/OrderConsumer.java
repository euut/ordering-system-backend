package com.euut.ecom.orderingsystem.service.consumer;

import com.euut.ecom.orderingsystem.config.RabbitMQConfig;
import com.euut.ecom.orderingsystem.dto.OrderEventDto;
import com.euut.ecom.orderingsystem.service.OrderService;
import com.euut.ecom.orderingsystem.service.ProductService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

//@Component
//@RequiredArgsConstructor
//public class OrderConsumer {
//
//    private final OrderService orderService;
//
//    @RabbitListener(queues = RabbitMQConfig.QUEUE)
//    public void consumeOrderEvent(
//            OrderEventDto event,
//            Channel channel,
//            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag
//    ) throws IOException {
//        try {
//            orderService.fulfillOrder(event);
//
//            // Acknowledge queue receipt
//            channel.basicAck(deliveryTag, false);
//
//        } catch (Exception ex) {
//            // Reject message without requeuing (Redis restoration already executed inside fulfillOrder)
//            channel.basicReject(deliveryTag, false);
//        }
//    }
//}

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderConsumer {

    private final OrderService orderService;
    private final ProductService productService;

    @RabbitListener(queues = RabbitMQConfig.QUEUE)
    public void consumeOrderEvent(OrderEventDto event) {
        try {
            orderService.fulfillOrder(event);
        } catch (Exception ex) {
            log.error("Fulfillment failed for order {}. Restoring Redis stock.", event.orderId(), ex);

            // Explicitly restore Redis inventory on DB failure
            productService.restoreBatchStock(event.items());

            // Stops RabbitMQ from retrying this failed message forever
            // forces the message out of the queue so it cannot rerun restoreBatchStock function
            throw new org.springframework.amqp.AmqpRejectAndDontRequeueException("Fulfillment failed", ex);
        }
    }
}