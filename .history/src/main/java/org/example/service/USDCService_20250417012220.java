package org.example.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.persistence.*;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.example.model.USDCBalance;
import org.example.repository.UsdcBalanceRepository;

@Service
@RequiredArgsConstructor
public class USDCService {
    private final UsdcBalanceRepository usdcBalanceRepository;

    @Transactional
    public void initializeBalance(String address, BigDecimal amount) {
        // 創建或更新 USDC 餘額
        USDCBalance balance = usdcBalanceRepository.findByAddress(address)
            .orElse(new USDCBalance());

        balance.setAddress(address);
        balance.setBalance(amount);

        usdcBalanceRepository.save(balance);
    }

    @Transactional(readOnly = true)
    public BigDecimal getBalance(String address) {
        return usdcBalanceRepository.findByAddress(address)
            .map(USDCBalance::getBalance)
            .orElse(BigDecimal.ZERO);
    }

    @Transactional
    public void transfer(String from, String to, BigDecimal amount) {
        USDCBalance fromBalance = usdcBalanceRepository.findByAddress(from)
            .orElseThrow(() -> new IllegalStateException("Insufficient USDC balance"));

        if (fromBalance.getBalance().compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient USDC balance");
        }

        // 扣除發送方餘額
        fromBalance.setBalance(fromBalance.getBalance().subtract(amount));
        usdcBalanceRepository.save(fromBalance);

        // 增加接收方餘額
        USDCBalance toBalance = usdcBalanceRepository.findByAddress(to)
            .orElse(new USDCBalance());
        
        toBalance.setAddress(to);
        toBalance.setBalance(toBalance.getBalance().add(amount));
        
        usdcBalanceRepository.save(toBalance);
    }
} 