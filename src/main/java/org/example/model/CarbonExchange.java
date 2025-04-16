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

    @OneToOne
    @JoinColumn(name = "token_id")
    private CarbonCreditToken token;

    @Column(name = "carbon_fee_reserves")
    private BigDecimal carbonFeeReserves = BigDecimal.ZERO;

    @Column(name = "base_fee_reserves")
    private BigDecimal baseFeeReserves = BigDecimal.ZERO;
} 