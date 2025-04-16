package org.example.model;

import javax.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Entity
@NoArgsConstructor
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

    @Column(name = "shares", nullable = false, precision = 38, scale = 18)
    private BigDecimal shares;
} 