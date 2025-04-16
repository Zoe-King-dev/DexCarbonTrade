package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.model.CarbonExchange;
import org.example.model.LiquidityInfo;
import org.example.repository.CarbonExchangeRepository;
import org.example.repository.CarbonCreditTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class CarbonExchangeService {
    private final CarbonExchangeRepository exchangeRepository;
    private final CarbonCreditTokenRepository tokenRepository;

    @Transactional
    public CarbonExchange createExchange(String adminAddress) {
        CarbonCreditToken token = tokenRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Token not found"));
        
        CarbonExchange exchange = new CarbonExchange(token);
        return exchangeRepository.save(exchange);
    }

    @Transactional
    public void createPool(String adminAddress, BigDecimal amountCarbonCredits, BigDecimal amountBaseCurrency) {
        CarbonExchange exchange = exchangeRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Exchange not found"));
        
        if (!exchange.getToken().getAdminAddress().equals(adminAddress)) {
            throw new IllegalStateException("Only admin can create pool");
        }
        
        exchange.createPool(amountCarbonCredits, amountBaseCurrency);
        exchangeRepository.save(exchange);
    }

    @Transactional
    public void addLiquidity(String provider, BigDecimal amountBaseCurrency, 
                           BigDecimal minExchangeRate, BigDecimal maxExchangeRate) {
        CarbonExchange exchange = exchangeRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Exchange not found"));
        
        exchange.addLiquidity(provider, amountBaseCurrency, minExchangeRate, maxExchangeRate);
        exchangeRepository.save(exchange);
    }

    @Transactional
    public void removeLiquidity(String provider, BigDecimal amountBaseCurrency,
                              BigDecimal minExchangeRate, BigDecimal maxExchangeRate) {
        CarbonExchange exchange = exchangeRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Exchange not found"));
        
        exchange.removeLiquidity(provider, amountBaseCurrency, minExchangeRate, maxExchangeRate);
        exchangeRepository.save(exchange);
    }

    @Transactional
    public void swapCarbonCreditsForBaseCurrency(String trader, BigDecimal amountCarbonCredits,
                                               BigDecimal maxExchangeRate) {
        CarbonExchange exchange = exchangeRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Exchange not found"));
        
        exchange.swapCarbonCreditsForBaseCurrency(trader, amountCarbonCredits, maxExchangeRate);
        exchangeRepository.save(exchange);
    }

    @Transactional
    public void swapBaseCurrencyForCarbonCredits(String trader, BigDecimal amountBaseCurrency,
                                               BigDecimal maxExchangeRate) {
        CarbonExchange exchange = exchangeRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Exchange not found"));
        
        exchange.swapBaseCurrencyForCarbonCredits(trader, amountBaseCurrency, maxExchangeRate);
        exchangeRepository.save(exchange);
    }

    public LiquidityInfo getLiquidity(String address) {
        CarbonExchange exchange = exchangeRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Exchange not found"));
        
        LiquidityInfo info = new LiquidityInfo();
        info.setCarbon(exchange.getLiquidity(address));
        info.setBase(exchange.getBaseCurrencyReserves());
        return info;
    }

    public BigDecimal calculateExchangeRate() {
        CarbonExchange exchange = exchangeRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Exchange not found"));
        
        return exchange.getCarbonCreditReserves()
                .multiply(BigDecimal.valueOf(exchange.getExchangeRateMultiplier()))
                .divide(exchange.getBaseCurrencyReserves(), 18, BigDecimal.ROUND_DOWN);
    }

    public BigDecimal getBaseBalance(String address) {
        CarbonExchange exchange = exchangeRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Exchange not found"));
        
        return exchange.getBaseCurrencyReserves();
    }
} 