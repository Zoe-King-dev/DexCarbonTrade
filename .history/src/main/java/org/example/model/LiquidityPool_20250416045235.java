package org.example.model;

import javax.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Data
@Entity
@Table(name = "liquidity_pools")
public class LiquidityPool {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "exchange_id", nullable = false)
    private Long exchangeId;

    @Column(name = "total_carbon_reserves", nullable = false, columnDefinition = "DECIMAL(38,18) DEFAULT 0")
    private BigDecimal totalCarbonReserves = BigDecimal.ZERO;

    @Column(name = "total_base_reserves", nullable = false, columnDefinition = "DECIMAL(38,18) DEFAULT 0")
    private BigDecimal totalBaseReserves = BigDecimal.ZERO;

    @Column(name = "total_shares", nullable = false, columnDefinition = "DECIMAL(38,18) DEFAULT 0")
    private BigDecimal totalShares = BigDecimal.ZERO;

    @Column(name = "k", nullable = false, columnDefinition = "DECIMAL(38,18) DEFAULT 0")
    private BigDecimal k = BigDecimal.ZERO;

    @Column(name = "exchange_rate_multiplier", nullable = false, columnDefinition = "DECIMAL(38,18) DEFAULT 100000")
    private BigDecimal exchangeRateMultiplier = new BigDecimal("100000");

    @OneToMany(mappedBy = "pool", cascade = CascadeType.ALL)
    private List<LiquidityProvider> liquidityProviders = new ArrayList<>();

    @Column(name = "base_reserves", precision = 36, scale = 18)
    private BigDecimal baseReserves;

    @Column(name = "carbon_reserves", precision = 36, scale = 18)
    private BigDecimal carbonReserves;

    @ManyToOne
    @JoinColumn(name = "exchange_id", nullable = false)
    private CarbonExchange exchange;

    public LiquidityPool() {
        this.exchangeId = 1L; // 設置默認的 exchange_id
    }

    public BigDecimal getLiquidity(String address) {
        return liquidityProviders.stream()
                .filter(p -> p.getProviderAddress().equals(address))
                .findFirst()
                .map(LiquidityProvider::getShares)
                .orElse(BigDecimal.ZERO);
    }

    public BigDecimal getUserSharePercentage(String address) {
        if (totalShares.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return getLiquidity(address).divide(totalShares, 18, RoundingMode.HALF_UP);
    }

    public void addLiquidityProvider(String address, BigDecimal shares) {
        LiquidityProvider provider = new LiquidityProvider();
        provider.setProviderAddress(address);
        provider.setShares(shares);
        provider.setPool(this);
        liquidityProviders.add(provider);
    }

    public BigDecimal getBaseReserves() {
        return baseReserves;
    }

    public void setBaseReserves(BigDecimal baseReserves) {
        this.baseReserves = baseReserves;
    }

    public BigDecimal getCarbonReserves() {
        return carbonReserves;
    }

    public void setCarbonReserves(BigDecimal carbonReserves) {
        this.carbonReserves = carbonReserves;
    }

    public CarbonExchange getExchange() {
        return exchange;
    }

    public void setExchange(CarbonExchange exchange) {
        this.exchange = exchange;
    }

    public BigDecimal calculateExchangeRate() {
        if (baseReserves.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return carbonReserves.divide(baseReserves, 18, RoundingMode.HALF_UP);
    }
} 