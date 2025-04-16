package org.example.model;

import lombok.Data;
import javax.persistence.*;
import java.math.BigDecimal;

@Data
@Entity
@Table(name = "carbon_exchanges")
public class CarbonExchange {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "admin_address", nullable = false)
    private String adminAddress;

    @Column(name = "base_reserves", nullable = false, columnDefinition = "DECIMAL(38,18) DEFAULT 0")
    private BigDecimal baseReserves = BigDecimal.ZERO;

    @Column(name = "token_reserves", nullable = false, columnDefinition = "DECIMAL(38,18) DEFAULT 0")
    private BigDecimal tokenReserves = BigDecimal.ZERO;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "pool_id")
    private LiquidityPool liquidityPool;

    @OneToOne
    @JoinColumn(name = "token_id")
    private CarbonCreditToken token;

    @Column(name = "carbon_fee_reserves")
    private BigDecimal carbonFeeReserves = BigDecimal.ZERO;

    @Column(name = "base_fee_reserves")
    private BigDecimal baseFeeReserves = BigDecimal.ZERO;
} 