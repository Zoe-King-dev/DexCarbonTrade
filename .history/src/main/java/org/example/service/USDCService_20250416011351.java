package org.example.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.persistence.*;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class USDCService {
    @PersistenceContext
    private EntityManager entityManager;

    @Transactional
    public void initializeBalance(String address, BigDecimal amount) {
        // 使用原生 SQL 更新或插入 USDC 餘額
        entityManager.createNativeQuery(
            "INSERT INTO usdc_balances (address, balance) VALUES (:address, :amount) " +
            "ON DUPLICATE KEY UPDATE balance = balance + :amount")
            .setParameter("address", address)
            .setParameter("amount", amount)
            .executeUpdate();
    }

    @Transactional(readOnly = true)
    public BigDecimal getBalance(String address) {
        try {
            Object result = entityManager.createNativeQuery(
                "SELECT balance FROM usdc_balances WHERE address = :address")
                .setParameter("address", address)
                .getSingleResult();
            return result != null ? new BigDecimal(result.toString()) : BigDecimal.ZERO;
        } catch (NoResultException e) {
            return BigDecimal.ZERO;
        }
    }

    @Transactional
    public void transfer(String from, String to, BigDecimal amount) {
        BigDecimal fromBalance = getBalance(from);
        if (fromBalance.compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient USDC balance");
        }

        // 扣除發送方餘額
        entityManager.createNativeQuery(
            "UPDATE usdc_balances SET balance = balance - :amount WHERE address = :address")
            .setParameter("amount", amount)
            .setParameter("address", from)
            .executeUpdate();

        // 增加接收方餘額
        entityManager.createNativeQuery(
            "INSERT INTO usdc_balances (address, balance) VALUES (:address, :amount) " +
            "ON DUPLICATE KEY UPDATE balance = balance + :amount")
            .setParameter("address", to)
            .setParameter("amount", amount)
            .executeUpdate();
    }
} 