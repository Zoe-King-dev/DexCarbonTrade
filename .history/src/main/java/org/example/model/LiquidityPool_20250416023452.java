package org.example.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import javax.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

@Data
@Entity
@Table(name = "liquidity_pools")
public class LiquidityPool {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "exchange_id")
    private CarbonExchange exchange;

    @Column(nullable = false, columnDefinition = "DECIMAL(36,18) DEFAULT 0")
    private BigDecimal totalCarbonReserves = BigDecimal.ZERO;

    @Column(nullable = false, columnDefinition = "DECIMAL(36,18) DEFAULT 0")
    private BigDecimal totalBaseReserves = BigDecimal.ZERO;

    @Column(nullable = false, columnDefinition = "DECIMAL(36,18) DEFAULT 0")
    private BigDecimal totalShares = BigDecimal.ZERO;

    @Column(nullable = false, columnDefinition = "DECIMAL(36,18) DEFAULT 0")
    private BigDecimal k = BigDecimal.ZERO;

    @Column(nullable = false, columnDefinition = "DECIMAL(36,18) DEFAULT 100000")
    private BigDecimal exchangeRateMultiplier = BigDecimal.valueOf(10).pow(5);

    @ElementCollection
    @CollectionTable(name = "liquidity_providers", joinColumns = @JoinColumn(name = "pool_id"))
    @MapKeyColumn(name = "provider_address")
    @Column(name = "shares")
    private Map<String, BigDecimal> liquidityProviders = new HashMap<>();

    public LiquidityPool() {
        this.totalCarbonReserves = BigDecimal.ZERO;
        this.totalBaseReserves = BigDecimal.ZERO;
        this.totalShares = BigDecimal.ZERO;
        this.k = BigDecimal.ZERO;
        this.exchangeRateMultiplier = BigDecimal.valueOf(10).pow(5);
    }

    public LiquidityPool(CarbonExchange exchange) {
        this();
        this.exchange = exchange;
    }

    @PrePersist
    public void prePersist() {
        if (this.totalCarbonReserves == null) {
            this.totalCarbonReserves = BigDecimal.ZERO;
        }
        if (this.totalBaseReserves == null) {
            this.totalBaseReserves = BigDecimal.ZERO;
        }
        if (this.totalShares == null) {
            this.totalShares = BigDecimal.ZERO;
        }
        if (this.k == null) {
            this.k = BigDecimal.ZERO;
        }
        if (this.exchangeRateMultiplier == null) {
            this.exchangeRateMultiplier = BigDecimal.valueOf(10).pow(5);
        }
    }

    public void addLiquidityProvider(String address, BigDecimal shares) {
        liquidityProviders.merge(address, shares, BigDecimal::add);
    }

    public BigDecimal getLiquidity(String address) {
        return liquidityProviders.getOrDefault(address, BigDecimal.ZERO);
    }

    public void removeLiquidityProvider(String address, BigDecimal shares) {
        liquidityProviders.merge(address, shares.negate(), BigDecimal::add);
        if (liquidityProviders.get(address).compareTo(BigDecimal.ZERO) <= 0) {
            liquidityProviders.remove(address);
        }
    }

    public BigDecimal getUserSharePercentage(String address) {
        if (totalShares.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return getLiquidity(address).divide(totalShares, 18, RoundingMode.HALF_UP);
    }
} 