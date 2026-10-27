# Asynchronous Order Processing System

An asynchronous order processing backend built with Spring Boot, PostgreSQL, Redis, and RabbitMQ, with services orchestrated using Docker Compose.

## Problem

Imagine an online store with only 1 items left in stock, but 100 customers try to purchase them at the same time. If multiple customers manage to purchase the last available item, the store might end up accepting more orders than it can fulfill, causing overselling. On top of that, processing every order immediately can overwhelm the system during sudden traffic spikes, causing long waiting times and slow responses. The challenge is simple: How can an online store handle thousands of simultaneous orders quickly without overselling its limited stock?

## Architecture

<img width="2172" height="724" alt="image" src="https://github.com/user-attachments/assets/8bac2999-3434-48fe-a452-1f1e26b68cf8" />

## Tech Stack

| Technology | Purpose |
| --- | --- |
| Java 21 | Application language |
| Spring Boot | REST API and application framework |
| Spring Security, JWT | Authentication and endpoint protection |
| Spring Data JPA | Relational persistence |
| PostgreSQL 16 | Products, users, orders, and transactional stock updates |
| Redis 7 | Atomic stock reservations using Lua scripts and caching |
| RabbitMQ 3 | Asynchronous order events and dead-letter routing |
| Flyway | Database schema migrations |
| Docker, Docker Compose | Containerized application and dependencies |
| Maven | Build and dependency management |
| JUnit, Testcontainers, Awaitility | Integration and asynchronous testing |
| Springdoc OpenAPI | API documentation |

## Setup

### Prerequisites

- [Docker Desktop](https://www.docker.com/get-started/) with Docker Compose

### Build & Run

Clone the repository:

```bash
git clone https://github.com/euut/ordering-system-backend.git
cd ordering-system-backend
```

Build and start all services:

```bash
docker compose up --build
```

Once the application starts, access:

- **API**: `http://localhost:8080`
- **Swagger UI**: `http://localhost:8080/swagger-ui/index.html`
- **RabbitMQ Management**: `http://localhost:15672`

**RabbitMQ's management credentials**: currently `guest` / `guest` for local development.

## API Endpoints

Base URL: `http://localhost:8080/api/v1`

| Method | Endpoint | Description |
| --- | --- | --- |
| POST | `/auth/register` | Register a user and receive a JWT |
| POST | `/auth/login` | Log in and receive a JWT |
| GET | `/products/{sku}` | Retrieve a product by SKU |
| POST | `/products` | Create/save a product |
| POST | `/orders` | Submit an order for asynchronous processing |
| GET | `/orders/{orderId}` | Retrieve a persisted order's status |

For the full interactive API reference, use Swagger UI after starting the application (see above).

## Testing

Run test:

```bash
# 3 integration tests covered
./mvnw test
```

## Future Improvements

- Prevent order loss between Redis reservations and RabbitMQ publishing.
- Improve synchronization between Redis and PostgreSQL during failures.
- Strengthen protection against concurrent duplicate order processing.
- Allow users to check PENDING orders before fulfillment.
- Introduce USER and ADMIN roles for customer and inventory management operations.
