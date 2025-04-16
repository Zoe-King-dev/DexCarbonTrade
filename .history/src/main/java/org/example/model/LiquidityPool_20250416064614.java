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

    @Column(name = "total_base_reserves", precision = 36, scale = 18)
    private BigDecimal usdcReserves;

    @Column(name = "total_carbon_reserves", precision = 36, scale = 18)
    private BigDecimal cctReserves;

    @Column(name = "total_shares", precision = 36, scale = 18, nullable = false)
    private BigDecimal totalShares = BigDecimal.ZERO;

    @Column(name = "exchange_rate_multiplier", precision = 36, scale = 18, nullable = false)
    private BigDecimal exchangeRateMultiplier = BigDecimal.ONE;

    @Column(name = "k", precision = 36, scale = 18, nullable = false)
    private BigDecimal k = BigDecimal.ZERO;

    @OneToMany(mappedBy = "pool", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LiquidityProvider> liquidityProviders = new ArrayList<>();

    public LiquidityPool() {
        this.usdcReserves = BigDecimal.ZERO;
        this.cctReserves = BigDecimal.ZERO;
        this.totalShares = BigDecimal.ZERO;
        this.exchangeRateMultiplier = BigDecimal.ONE;
        this.k = BigDecimal.ZERO;
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