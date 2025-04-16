package org.example.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import javax.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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

    @OneToMany(mappedBy = "pool", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LiquidityProvider> liquidityProviders = new ArrayList<>();

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
        LiquidityProvider provider = new LiquidityProvider();
        provider.setProviderAddress(address);
        provider.setShares(shares);
        provider.setPool(this);
        provider.setExchange(this.getExchange());
        liquidityProviders.add(provider);
    }

    public BigDecimal getLiquidity(String address) {
        return liquidityProviders.stream()
                .filter(lp -> lp.getProviderAddress().equals(address))
                .map(LiquidityProvider::getShares)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    public void removeLiquidityProvider(String address, BigDecimal shares) {
        liquidityProviders.removeIf(lp -> lp.getProviderAddress().equals(address) && lp.getShares().compareTo(shares) == 0);
    }

    public BigDecimal getUserSharePercentage(String address) {
        if (totalShares.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return getLiquidity(address).divide(totalShares, 18, RoundingMode.HALF_UP);
    }
} 