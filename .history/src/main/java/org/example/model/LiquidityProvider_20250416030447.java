package org.example.model;

import jakarta.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Entity
@Table(name = "liquidity_providers")
public class LiquidityProvider {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "pool_id", nullable = false)
    private LiquidityPool pool;

    @Column(name = "provider_address", nullable = false)
    private String providerAddress;

    @Column(nullable = false)
    private BigDecimal shares;
} 