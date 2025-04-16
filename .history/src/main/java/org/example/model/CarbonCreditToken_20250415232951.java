package org.example.model;

import lombok.Data;
import javax.persistence.*;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Data
@Entity
@Table(name = "carbon_credit_tokens")
public class CarbonCreditToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String symbol;

    @Column(nullable = false)
    private String adminAddress;

    @Column(nullable = false)
    private boolean mintingEnabled = true;

    @ElementCollection
    @CollectionTable(name = "token_balances", joinColumns = @JoinColumn(name = "token_id"))
    @MapKeyColumn(name = "address")
    @Column(name = "balance")
    private Map<String, BigDecimal> balances = new HashMap<>();

    @ElementCollection
    @CollectionTable(name = "token_allowances", joinColumns = @JoinColumn(name = "token_id"))
    @MapKeyColumn(name = "spender")
    @Column(name = "amount")
    private Map<String, BigDecimal> allowances = new HashMap<>();

    public CarbonCreditToken(String adminAddress) {
        this.adminAddress = adminAddress;
    }

    public void mint(String to, BigDecimal amount) {
        if (!mintingEnabled) {
            throw new IllegalStateException("Minting is disabled");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        totalSupply = totalSupply.add(amount);
        balances.merge(to, amount, BigDecimal::add);
    }

    public void disableMinting() {
        if (!mintingEnabled) {
            throw new IllegalStateException("Minting is already disabled");
        }
        mintingEnabled = false;
    }

    public void transfer(String from, String to, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        BigDecimal fromBalance = balances.getOrDefault(from, BigDecimal.ZERO);
        if (fromBalance.compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient balance");
        }
        balances.put(from, fromBalance.subtract(amount));
        balances.merge(to, amount, BigDecimal::add);
    }

    public void approve(String owner, String spender, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Amount cannot be negative");
        }
        allowances.computeIfAbsent(owner, k -> new HashMap<>())
                .put(spender, amount);
    }

    public void transferFrom(String owner, String spender, String to, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }
        BigDecimal allowance = allowances.getOrDefault(owner, new HashMap<>())
                .getOrDefault(spender, BigDecimal.ZERO);
        if (allowance.compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient allowance");
        }
        BigDecimal ownerBalance = balances.getOrDefault(owner, BigDecimal.ZERO);
        if (ownerBalance.compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient balance");
        }
        balances.put(owner, ownerBalance.subtract(amount));
        balances.merge(to, amount, BigDecimal::add);
        allowances.get(owner).put(spender, allowance.subtract(amount));
    }

    public BigDecimal balanceOf(String address) {
        return balances.getOrDefault(address, BigDecimal.ZERO);
    }

    public BigDecimal allowance(String owner, String spender) {
        return allowances.getOrDefault(owner, new HashMap<>())
                .getOrDefault(spender, BigDecimal.ZERO);
    }
} 