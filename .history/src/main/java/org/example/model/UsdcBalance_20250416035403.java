package org.example.model;

import javax.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Entity
@Table(name = "usdc_balances")
public class UsdcBalance {
    @Id
    @Column(name = "address", nullable = false)
    private String address;

    @ManyToOne
    @JoinColumn(name = "exchange_id", nullable = false)
    private CarbonExchange exchange;

    @Column(name = "balance", nullable = false, columnDefinition = "DECIMAL(36,18) DEFAULT 0")
    private BigDecimal balance;
} 