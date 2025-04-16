package org.example.model;

import javax.persistence.*;
import lombok.Data;
import java.math.BigDecimal;

@Data
@Entity
@Table(name = "usdc_balances")
public class USDCBalance {
    @Id
    @Column(name = "address", nullable = false)
    private String address;

    @Column(name = "balance", precision = 36, scale = 18, nullable = false)
    private BigDecimal balance;

    @Column(name = "exchange_id", nullable = false)
    private Long exchangeId;

    @ManyToOne
    @JoinColumn(name = "exchange_id", insertable = false, updatable = false)
    private LiquidityPool pool;

    public void setPool(LiquidityPool pool) {
        this.pool = pool;
        this.exchangeId = pool.getExchangeId();
    }
} 