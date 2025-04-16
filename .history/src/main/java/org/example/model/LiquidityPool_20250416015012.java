package org.example.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import javax.persistence.*;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
@Entity
@Table(name = "liquidity_pools")
public class LiquidityPool {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "exchange_id")
    private CarbonExchange exchange;

    @Column(nullable = false)
    private BigDecimal totalCarbonReserves = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal totalBaseReserves = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal totalShares = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal k = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal exchangeRateMultiplier = BigDecimal.valueOf(10).pow(5);

    public LiquidityPool(CarbonExchange exchange) {
        this.exchange = exchange;
    }
} 