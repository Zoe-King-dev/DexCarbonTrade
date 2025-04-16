package org.example.model;

import javax.persistence.*;
import lombok.Data;
import java.math.BigDecimal;
import java.io.Serializable;

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

    @Column(nullable = false)
    private BigDecimal shares;
}

@Data
class LiquidityProviderId implements Serializable {
    private Long exchange;
    private String providerAddress;
} 