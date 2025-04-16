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

    @Column(name = "usdc_reserves", precision = 36, scale = 18)
    private BigDecimal usdcReserves = BigDecimal.ZERO;

    @Column(name = "cct_reserves", precision = 36, scale = 18)
    private BigDecimal cctReserves = BigDecimal.ZERO;

    @Column(name = "total_shares", precision = 36, scale = 18)
    private BigDecimal totalShares = BigDecimal.ZERO;

    @Column(name = "k", nullable = false, columnDefinition = "DECIMAL(38,18) DEFAULT 0")
    private BigDecimal k = BigDecimal.ZERO;

    @Column(name = "exchange_rate_multiplier", nullable = false, columnDefinition = "DECIMAL(38,18) DEFAULT 100000")
    private BigDecimal exchangeRateMultiplier = new BigDecimal("100000");

    @OneToMany(mappedBy = "pool", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LiquidityProvider> liquidityProviders = new ArrayList<>();

    public LiquidityPool() {
        // 移除 exchangeId 的設置
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

    public void addLiquidityProvider(String providerAddress, BigDecimal shares) {
        LiquidityProvider provider = new LiquidityProvider();
        provider.setProviderAddress(providerAddress);
        provider.setShares(shares);
        provider.setPool(this);
        liquidityProviders.add(provider);
    }

    public BigDecimal calculateExchangeRate() {
        if (cctReserves.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return usdcReserves.divide(cctReserves, 18, RoundingMode.HALF_UP);
    }

    public BigDecimal getCctReserves() {
        return cctReserves;
    }

    public BigDecimal getUsdcReserves() {
        return usdcReserves;
    }
} 