package org.example.model;

import lombok.Data;
import javax.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "transactions")
public class Transaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "from_address", nullable = false)
    private String fromAddress;

    @Column(name = "to_address", nullable = false)
    private String toAddress;

    @Column(name = "amount", precision = 36, scale = 18)
    private BigDecimal amount;

    @Column(name = "token_address")
    private String tokenAddress;

    @Column(name = "type")
    private String type; // "MINT", "TRANSFER", "SWAP", "ADD_LIQUIDITY", "REMOVE_LIQUIDITY"

    @Column(name = "timestamp")
    private LocalDateTime timestamp = LocalDateTime.now();

    @Column
    private BigDecimal amount2; // 用於交換操作的第二個金額

    @Column
    private String status; // "SUCCESS", "FAILED"

    @PrePersist
    protected void onCreate() {
        timestamp = LocalDateTime.now();
    }

    public void setTokenAddress(String tokenAddress) {
        this.tokenAddress = tokenAddress;
    }
}
 