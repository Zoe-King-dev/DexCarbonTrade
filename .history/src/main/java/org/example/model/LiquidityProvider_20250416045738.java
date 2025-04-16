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
    @Column(name = "pool_id", nullable = false)
    private Long poolId;

    @Id
    @Column(name = "provider_address", nullable = false)
    private String providerAddress;

    @ManyToOne
    @JoinColumn(name = "pool_id", insertable = false, updatable = false)
    private LiquidityPool pool;

    @Column(name = "shares", nullable = false, columnDefinition = "DECIMAL(38,18) DEFAULT 0")
    private BigDecimal shares = BigDecimal.ZERO;
} 