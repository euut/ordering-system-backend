CREATE TABLE users (
                       id BIGSERIAL PRIMARY KEY,
                       email VARCHAR(255) UNIQUE NOT NULL,
                       password_hash VARCHAR(255) NOT NULL
);

CREATE TABLE products (
                          id BIGSERIAL PRIMARY KEY,
                          sku VARCHAR(100) UNIQUE NOT NULL,
                          price DECIMAL(10, 2) NOT NULL CHECK (price >= 0),
                          available_stock INTEGER NOT NULL DEFAULT 0 CHECK (available_stock >= 0)
);

CREATE TABLE orders (
                        id BIGSERIAL PRIMARY KEY,
                        order_id VARCHAR(64) UNIQUE NOT NULL,
                        user_id BIGINT NOT NULL,
                        total_amount DECIMAL(10, 2) NOT NULL CHECK (total_amount >= 0),
                        status VARCHAR(50) NOT NULL CHECK (status IN ('PENDING', 'PROCESSING', 'COMPLETED', 'CANCELLED', 'FAILED')),
                        items JSONB NOT NULL,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        CONSTRAINT fk_order_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX idx_orders_user_id ON orders(user_id);