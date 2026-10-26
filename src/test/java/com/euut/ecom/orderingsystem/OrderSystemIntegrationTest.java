package com.euut.ecom.orderingsystem;

import com.euut.ecom.orderingsystem.config.RabbitMQConfig;
import com.euut.ecom.orderingsystem.dto.CreateOrderRequestDto;
import com.euut.ecom.orderingsystem.dto.OrderEventDto;
import com.euut.ecom.orderingsystem.dto.OrderItemRequestDto;
import com.euut.ecom.orderingsystem.dto.OrderResponseDto;
import com.euut.ecom.orderingsystem.entity.Order;
import com.euut.ecom.orderingsystem.entity.OrderStatus;
import com.euut.ecom.orderingsystem.entity.Product;
import com.euut.ecom.orderingsystem.entity.User;
import com.euut.ecom.orderingsystem.repository.OrderRepository;
import com.euut.ecom.orderingsystem.repository.ProductRepository;
import com.euut.ecom.orderingsystem.repository.UserRepository;
import com.euut.ecom.orderingsystem.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public class OrderSystemIntegrationTest {

	@Container
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

	@Container
	static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

	@Container
	static RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3-management-alpine");

	// bind random container ports to Spring environment
	@DynamicPropertySource
	static void configureProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", postgres::getJdbcUrl);
		registry.add("spring.datasource.username", postgres::getUsername);
		registry.add("spring.datasource.password", postgres::getPassword);

		registry.add("spring.data.redis.host", redis::getHost);
		registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));

		registry.add("spring.rabbitmq.host", rabbitmq::getHost);
		registry.add("spring.rabbitmq.port", rabbitmq::getAmqpPort);
	}

	@Autowired
	private OrderService orderService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private ProductRepository productRepository;

	@Autowired
	private OrderRepository orderRepository;

	@Autowired
	private StringRedisTemplate redisTemplate;

	@Autowired
	private RabbitTemplate rabbitTemplate;

	@Autowired
	private RabbitAdmin rabbitAdmin;

	private User testUser;

	@BeforeEach
	void setUp() {
		// Clear databases and queues between tests
		orderRepository.deleteAll();
		productRepository.deleteAll();
		userRepository.deleteAll();
		redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
		rabbitAdmin.purgeQueue(RabbitMQConfig.QUEUE);
		rabbitAdmin.purgeQueue(RabbitMQConfig.DLQ_QUEUE);

		// Seed default user
		testUser = new User();
		testUser.setEmail("user@example.com");
		testUser.setPasswordHash("hashed_password");
		userRepository.save(testUser);
	}

	@Test
	void shouldProcessOrderAndFulfillStockAsynchronously() {
		// Arrange
		String sku = "LAPTOP-X";
		Product product = new Product();
		product.setSku(sku);
		product.setPrice(BigDecimal.valueOf(1200.00));
		product.setAvailableStock(10);
		productRepository.save(product);

		// Sync Redis cache with DB stock
		redisTemplate.opsForValue().set("product:stock:" + sku, "10");

		CreateOrderRequestDto request = new CreateOrderRequestDto(
				List.of(new OrderItemRequestDto(sku, 2))
		);

		// Act 1: Call synchronous processOrder
		OrderResponseDto response = orderService.processOrder(testUser.getEmail(), request);

		// Assert 1: Fast-path return checks
		assertThat(response.orderId()).isNotNull();
		assertThat(response.status()).isEqualTo(OrderStatus.PENDING);

		// Assert 2: Redis stock immediately decremented (10 - 2 = 8)
		String redisStock = redisTemplate.opsForValue().get("product:stock:" + sku);
		assertThat(redisStock).isEqualTo("8");

		// Act 2: Wait for asynchronous consumer execution
		await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
			Order savedOrder = orderRepository.findByOrderIdAndUserId(response.orderId(), testUser.getId()).orElse(null);
			assertThat(savedOrder).isNotNull();
			assertThat(savedOrder.getStatus()).isEqualTo(OrderStatus.COMPLETED);
			assertThat(savedOrder.getTotalAmount()).isEqualByComparingTo(BigDecimal.valueOf(2400.00));
		});

		// Assert 3: Database stock decremented (10 - 2 = 8)
		Product updatedProduct = productRepository.findBySku(sku).orElseThrow();
		assertThat(updatedProduct.getAvailableStock()).isEqualTo(8);
	}

	@Test
	void shouldRestoreRedisStockAndDeadLetterMessage() {
		// Arrange
		String sku = "OUT-OF-SYNC-SKU";
		Product product = new Product();
		product.setSku(sku);
		product.setPrice(BigDecimal.valueOf(50.00));
		product.setAvailableStock(0); // DB has 0 stock
		productRepository.save(product);

		// Redis incorrectly has 5 items available
		redisTemplate.opsForValue().set("product:stock:" + sku, "5");

		CreateOrderRequestDto request = new CreateOrderRequestDto(
				List.of(new OrderItemRequestDto(sku, 2))
		);

		// Act: Submit request (Passes Redis validation, enqueues event)
		OrderResponseDto response = orderService.processOrder(testUser.getEmail(), request);

		// Redis drops to 3 during fast-path
		assertThat(redisTemplate.opsForValue().get("product:stock:" + sku)).isEqualTo("3");

		// Assert 1: Wait for Consumer to fail and restore Redis stock back to 5
		await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
			String restoredStock = redisTemplate.opsForValue().get("product:stock:" + sku);
			assertThat(restoredStock).isEqualTo("5");
		});

		// Assert 2: Verify order was NOT saved in database
		assertThat(orderRepository.existsByOrderId(response.orderId())).isFalse();

		// Assert 3: Verify message arrived in Dead-Letter Queue (DLQ)
		await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
			Object dlqMessage = rabbitTemplate.receiveAndConvert(RabbitMQConfig.DLQ_QUEUE);
			assertThat(dlqMessage).isNotNull();
			assertThat(dlqMessage).isInstanceOf(OrderEventDto.class);
			OrderEventDto event = (OrderEventDto) dlqMessage;
			assertThat(event.orderId()).isEqualTo(response.orderId());
		});
	}

	@Test
	void shouldIgnoreDuplicateEvents() {
		// Arrange
		String sku = "PHONE-1";
		Product product = new Product();
		product.setSku(sku);
		product.setPrice(BigDecimal.valueOf(500.00));
		product.setAvailableStock(10);
		productRepository.save(product);

		OrderEventDto event = new OrderEventDto(
				"DUPLICATE-ORDER-ID-123",
				testUser.getId(),
				List.of(new OrderItemRequestDto(sku, 1))
		);

		// Act 1: Fulfill order first time
		orderService.fulfillOrder(event);
		assertThat(orderRepository.existsByOrderId(event.orderId())).isTrue();
		assertThat(productRepository.findBySku(sku).orElseThrow().getAvailableStock()).isEqualTo(9);

		// Act 2: Redeliver identical event
		orderService.fulfillOrder(event);

		// Assert: Database stock remains 9 (Not decremented twice)
		assertThat(productRepository.findBySku(sku).orElseThrow().getAvailableStock()).isEqualTo(9);
	}
}