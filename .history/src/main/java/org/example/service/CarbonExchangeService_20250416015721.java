package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.model.CarbonExchange;
import org.example.model.LiquidityInfo;
import org.example.repository.CarbonExchangeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CarbonExchangeService {
    private final CarbonExchangeRepository exchangeRepository;

    @Transactional
    public CarbonExchange createExchange(String adminAddress) {
        CarbonExchange exchange = new CarbonExchange();
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
        
        exchange.getLiquidityPool().setTotalCarbonReserves(amountCarbonCredits);
        exchange.getLiquidityPool().setTotalBaseReserves(amountBaseCurrency);
        exchange.getLiquidityPool().setK(amountCarbonCredits.multiply(amountBaseCurrency));
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
        
        BigDecimal currentExchangeRate = exchange.calculateExchangeRate();
        if (currentExchangeRate.compareTo(minExchangeRate) < 0 || 
            currentExchangeRate.compareTo(maxExchangeRate) > 0) {
            throw new IllegalStateException("Exchange rate out of range");
        }

        BigDecimal sharesToRemove = amountBaseCurrency.multiply(exchange.getLiquidityPool().getTotalShares())
                .divide(exchange.getLiquidityPool().getTotalBaseReserves(), 18, BigDecimal.ROUND_DOWN);
        
        if (exchange.getLiquidityProviders().getOrDefault(provider, BigDecimal.ZERO).compareTo(sharesToRemove) < 0) {
            throw new IllegalStateException("Insufficient shares");
        }

        BigDecimal carbonCreditsToRemove = exchange.getLiquidityPool().getTotalCarbonReserves().multiply(sharesToRemove)
                .divide(exchange.getLiquidityPool().getTotalShares(), 18, BigDecimal.ROUND_DOWN);
        BigDecimal baseCurrencyToRemove = exchange.getLiquidityPool().getTotalBaseReserves().multiply(sharesToRemove)
                .divide(exchange.getLiquidityPool().getTotalShares(), 18, BigDecimal.ROUND_DOWN);

        exchange.getLiquidityPool().setTotalCarbonReserves(
            exchange.getLiquidityPool().getTotalCarbonReserves().subtract(carbonCreditsToRemove));
        exchange.getLiquidityPool().setTotalBaseReserves(
            exchange.getLiquidityPool().getTotalBaseReserves().subtract(baseCurrencyToRemove));
        exchange.getLiquidityPool().setTotalShares(
            exchange.getLiquidityPool().getTotalShares().subtract(sharesToRemove));
        
        exchange.getLiquidityProviders().merge(provider, sharesToRemove.negate(), BigDecimal::add);
        exchangeRepository.save(exchange);
    }

    @Transactional
    public void swapCarbonCreditsForBaseCurrency(String trader, BigDecimal amountCarbonCredits,
                                               BigDecimal maxExchangeRate) {
        CarbonExchange exchange = exchangeRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Exchange not found"));
        
        BigDecimal exchangeRate = exchange.calculateExchangeRate();
        if (exchangeRate.compareTo(maxExchangeRate) > 0) {
            throw new IllegalStateException("Slippage too large");
        }

        BigDecimal fee = amountCarbonCredits.multiply(BigDecimal.valueOf(3))
                .divide(BigDecimal.valueOf(100), 18, BigDecimal.ROUND_DOWN);
        BigDecimal amountAfterFee = amountCarbonCredits.subtract(fee);

        BigDecimal baseCurrencyOut = exchange.getLiquidityPool().getTotalBaseReserves().multiply(amountAfterFee)
                .divide(exchange.getLiquidityPool().getTotalCarbonReserves().add(amountAfterFee), 18, BigDecimal.ROUND_DOWN);

        if (baseCurrencyOut.compareTo(exchange.getLiquidityPool().getTotalBaseReserves()) >= 0) {
            throw new IllegalStateException("Insufficient base currency reserves");
        }

        exchange.getToken().transfer(trader, exchange.getToken().getAddress(), amountCarbonCredits);
        exchange.getLiquidityPool().setTotalCarbonReserves(
            exchange.getLiquidityPool().getTotalCarbonReserves().add(amountCarbonCredits));
        exchange.getLiquidityPool().setTotalBaseReserves(
            exchange.getLiquidityPool().getTotalBaseReserves().subtract(baseCurrencyOut));
        exchangeRepository.save(exchange);
    }

    @Transactional
    public void swapBaseCurrencyForCarbonCredits(String trader, BigDecimal amountBaseCurrency,
                                               BigDecimal maxExchangeRate) {
        CarbonExchange exchange = exchangeRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Exchange not found"));
        
        BigDecimal exchangeRate = exchange.calculateExchangeRate();
        if (exchangeRate.compareTo(maxExchangeRate) > 0) {
            throw new IllegalStateException("Slippage too large");
        }

        BigDecimal fee = amountBaseCurrency.multiply(BigDecimal.valueOf(3))
                .divide(BigDecimal.valueOf(100), 18, BigDecimal.ROUND_DOWN);
        BigDecimal amountAfterFee = amountBaseCurrency.subtract(fee);

        BigDecimal carbonCreditsOut = exchange.getLiquidityPool().getTotalCarbonReserves().multiply(amountAfterFee)
                .divide(exchange.getLiquidityPool().getTotalBaseReserves().add(amountAfterFee), 18, BigDecimal.ROUND_DOWN);

        if (carbonCreditsOut.compareTo(exchange.getLiquidityPool().getTotalCarbonReserves()) >= 0) {
            throw new IllegalStateException("Insufficient carbon credit reserves");
        }

        exchange.getLiquidityPool().setTotalBaseReserves(
            exchange.getLiquidityPool().getTotalBaseReserves().add(amountBaseCurrency));
        exchange.getLiquidityPool().setTotalCarbonReserves(
            exchange.getLiquidityPool().getTotalCarbonReserves().subtract(carbonCreditsOut));
        exchange.getToken().transfer(exchange.getToken().getAddress(), trader, carbonCreditsOut);
        exchangeRepository.save(exchange);
    }

    public CarbonExchange getExchange() {
        return exchangeRepository.findAll().stream()
                .findFirst()
                .orElseGet(() -> {
                    CarbonExchange exchange = new CarbonExchange();
                    return exchangeRepository.save(exchange);
                });
    }

    public LiquidityInfo getLiquidity(String address) {
        CarbonExchange exchange = getExchange();
        
        LiquidityInfo info = new LiquidityInfo();
        info.setUserLiquidity(exchange.getLiquidity(address));
        info.setTotalCarbonReserves(exchange.getLiquidityPool().getTotalCarbonReserves());
        info.setTotalBaseReserves(exchange.getLiquidityPool().getTotalBaseReserves());
        
        if (exchange.getLiquidityPool().getTotalBaseReserves().compareTo(BigDecimal.ZERO) > 0) {
            info.setSharePercentage(
                info.getUserLiquidity()
                    .multiply(new BigDecimal("100"))
                    .divide(exchange.getLiquidityPool().getTotalBaseReserves(), 4, BigDecimal.ROUND_HALF_UP)
            );
        } else {
            info.setSharePercentage(BigDecimal.ZERO);
        }
        
        info.setCurrentExchangeRate(exchange.calculateExchangeRate());
        return info;
    }

    public BigDecimal calculateExchangeRate() {
        CarbonExchange exchange = getExchange();
        return exchange.calculateExchangeRate();
    }

    public BigDecimal getBaseBalance(String address) {
        CarbonExchange exchange = getExchange();
        return exchange.getLiquidityPool().getTotalBaseReserves();
    }
} 