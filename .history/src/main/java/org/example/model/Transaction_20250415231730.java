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

    @Column(nullable = false)
    private String type; // "MINT", "TRANSFER", "SWAP", "ADD_LIQUIDITY", "REMOVE_LIQUIDITY"

    @Column(nullable = false)
    private String fromAddress;

    @Column(nullable = false)
    private String toAddress;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column
    private BigDecimal amount2; // 用於交換操作的第二個金額

    @Column(nullable = false)
    private LocalDateTime timestamp;

    @Column
    private String status; // "SUCCESS", "FAILED"

    @PrePersist
    protected void onCreate() {
        timestamp = LocalDateTime.now();
    }
}
 