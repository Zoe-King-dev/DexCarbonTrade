CREATE TABLE IF NOT EXISTS usdc_balances (
    address VARCHAR(255) PRIMARY KEY,
    balance DECIMAL(36,18) NOT NULL DEFAULT 0,
    exchange_id BIGINT NOT NULL,
    FOREIGN KEY (exchange_id) REFERENCES liquidity_pools(id)
); 