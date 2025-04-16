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
    @ManyToOne
    @JoinColumn(name = "exchange_id", nullable = false)
    private CarbonExchange exchange;

    @Id
    @Column(name = "provider_address", nullable = false)
    private String providerAddress;

    @ManyToOne
    @JoinColumn(name = "pool_id", nullable = false)
    private LiquidityPool pool;

    @Column(name = "shares", precision = 36, scale = 18)
    private BigDecimal shares = BigDecimal.ZERO;

    public void setExchange(CarbonExchange exchange) {
        this.exchange = exchange;
    }
} 