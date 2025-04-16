package org.example.model;

import javax.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Entity
@Table(name = "liquidity_providers")
@IdClass(LiquidityProviderId.class)
public class LiquidityProvider {
    @Id
    @Column(name = "exchange_id", nullable = false)
    private Long exchangeId;

    @Id
    @Column(name = "provider_address", nullable = false)
    private String providerAddress;

    @ManyToOne
    @JoinColumn(name = "pool_id", nullable = false)
    private LiquidityPool pool;

    @Column(name = "shares", precision = 36, scale = 18, nullable = false)
    private BigDecimal shares = BigDecimal.ZERO;

    public LiquidityProvider() {
        this.shares = BigDecimal.ZERO;
    }

    public void setExchange(CarbonExchange exchange) {
        this.exchangeId = exchange.getId();
    }

    public void setPool(LiquidityPool pool) {
        this.pool = pool;
    }

    public String getProviderAddress() {
        return providerAddress;
    }

    public void setProviderAddress(String providerAddress) {
        this.providerAddress = providerAddress;
    }

    public BigDecimal getShares() {
        return shares;
    }

    public void setShares(BigDecimal shares) {
        this.shares = shares;
    }

    public Long getExchangeId() {
        return exchangeId;
    }

    public void setExchangeId(Long exchangeId) {
        this.exchangeId = exchangeId;
    }

    public LiquidityPool getPool() {
        return pool;
    }
} 