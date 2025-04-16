package org.example.model;

import lombok.Data;
import javax.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

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

    @Column(nullable = false)
    private BigDecimal carbonCreditReserves = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal baseCurrencyReserves = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal k = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal exchangeRateMultiplier = BigDecimal.valueOf(10).pow(5);

    @ElementCollection
    @CollectionTable(name = "liquidity_providers", joinColumns = @JoinColumn(name = "exchange_id"))
    @MapKeyColumn(name = "provider_address")
    @Column(name = "shares")
    private Map<String, BigDecimal> liquidityProviders = new HashMap<>();

    public CarbonExchange() {}

    public CarbonExchange(CarbonCreditToken token) {
        this.token = token;
    }

    public void createPool(BigDecimal amountCarbonCredits, BigDecimal amountBaseCurrency) {
        if (carbonCreditReserves.compareTo(BigDecimal.ZERO) != 0 || 
            baseCurrencyReserves.compareTo(BigDecimal.ZERO) != 0) {
            throw new IllegalStateException("Pool already exists");
        }
        if (amountCarbonCredits.compareTo(BigDecimal.ZERO) <= 0 || 
            amountBaseCurrency.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amounts must be positive");
        }
        carbonCreditReserves = amountCarbonCredits;
        baseCurrencyReserves = amountBaseCurrency;
        k = carbonCreditReserves.multiply(baseCurrencyReserves);
    }

    public void addLiquidity(String provider, BigDecimal amountBaseCurrency, 
                           BigDecimal minExchangeRate, BigDecimal maxExchangeRate) {
        checkExchangeRate(minExchangeRate, maxExchangeRate);
        if (amountBaseCurrency.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        BigDecimal requiredCarbonCredits = amountBaseCurrency.multiply(carbonCreditReserves)
                .divide(baseCurrencyReserves, 18, RoundingMode.DOWN);
        
        if (token.balanceOf(provider).compareTo(requiredCarbonCredits) < 0) {
            throw new IllegalStateException("Insufficient carbon credits");
        }

        token.transferFrom(provider, provider, token.getAdminAddress(), requiredCarbonCredits);
        
        carbonCreditReserves = carbonCreditReserves.add(requiredCarbonCredits);
        baseCurrencyReserves = baseCurrencyReserves.add(amountBaseCurrency);
        k = carbonCreditReserves.multiply(baseCurrencyReserves);

        liquidityProviders.merge(provider, amountBaseCurrency, BigDecimal::add);
    }

    public void removeLiquidity(String provider, BigDecimal amountBaseCurrency,
                              BigDecimal minExchangeRate, BigDecimal maxExchangeRate) {
        checkExchangeRate(minExchangeRate, maxExchangeRate);
        if (amountBaseCurrency.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        BigDecimal sharesToRemove = amountBaseCurrency.multiply(BigDecimal.valueOf(100000))
                .divide(baseCurrencyReserves, 18, RoundingMode.DOWN);
        
        if (liquidityProviders.getOrDefault(provider, BigDecimal.ZERO).compareTo(sharesToRemove) < 0) {
            throw new IllegalStateException("Insufficient shares");
        }

        BigDecimal carbonCreditsToRemove = carbonCreditReserves.multiply(sharesToRemove)
                .divide(BigDecimal.valueOf(100000), 18, RoundingMode.DOWN);
        BigDecimal baseCurrencyToRemove = baseCurrencyReserves.multiply(sharesToRemove)
                .divide(BigDecimal.valueOf(100000), 18, RoundingMode.DOWN);

        if (carbonCreditsToRemove.compareTo(carbonCreditReserves) >= 0 || 
            baseCurrencyToRemove.compareTo(baseCurrencyReserves) >= 0) {
            throw new IllegalStateException("Cannot remove all liquidity");
        }

        // Calculate and distribute fees
        BigDecimal feeFraction = sharesToRemove.divide(BigDecimal.valueOf(100000), 18, RoundingMode.DOWN);
        BigDecimal carbonCreditFees = BigDecimal.ZERO;
        BigDecimal baseCurrencyFees = baseCurrencyReserves.multiply(feeFraction);

        // Update reserves and shares
        carbonCreditReserves = carbonCreditReserves.subtract(carbonCreditsToRemove);
        baseCurrencyReserves = baseCurrencyReserves.subtract(baseCurrencyToRemove);
        k = carbonCreditReserves.multiply(baseCurrencyReserves);
        liquidityProviders.put(provider, liquidityProviders.get(provider).subtract(sharesToRemove));

        // Transfer tokens and fees
        token.transfer(token.getAdminAddress(), provider, 
                carbonCreditsToRemove.add(carbonCreditFees));
        // Note: In a real implementation, base currency would be transferred here
    }

    public void swapCarbonCreditsForBaseCurrency(String trader, BigDecimal amountCarbonCredits,
                                               BigDecimal maxExchangeRate) {
        if (amountCarbonCredits.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        BigDecimal exchangeRate = calculateExchangeRate();
        if (exchangeRate.compareTo(maxExchangeRate) > 0) {
            throw new IllegalStateException("Slippage too large");
        }

        BigDecimal fee = amountCarbonCredits.multiply(BigDecimal.valueOf(3))
                .divide(BigDecimal.valueOf(100), 18, RoundingMode.DOWN);
        BigDecimal amountAfterFee = amountCarbonCredits.subtract(fee);

        BigDecimal baseCurrencyOut = baseCurrencyReserves.multiply(amountAfterFee)
                .divide(carbonCreditReserves.add(amountAfterFee), 18, RoundingMode.DOWN);

        if (baseCurrencyOut.compareTo(baseCurrencyReserves) >= 0) {
            throw new IllegalStateException("Insufficient base currency reserves");
        }

        token.transferFrom(trader, trader, token.getAdminAddress(), amountCarbonCredits);
        carbonCreditReserves = carbonCreditReserves.add(amountCarbonCredits);
        baseCurrencyReserves = baseCurrencyReserves.subtract(baseCurrencyOut);
    }

    public void swapBaseCurrencyForCarbonCredits(String trader, BigDecimal amountBaseCurrency,
                                               BigDecimal maxExchangeRate) {
        if (amountBaseCurrency.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be positive");
        }

        BigDecimal exchangeRate = calculateExchangeRate();
        if (exchangeRate.compareTo(maxExchangeRate) > 0) {
            throw new IllegalStateException("Slippage too large");
        }

        BigDecimal fee = amountBaseCurrency.multiply(BigDecimal.valueOf(3))
                .divide(BigDecimal.valueOf(100), 18, RoundingMode.DOWN);
        BigDecimal amountAfterFee = amountBaseCurrency.subtract(fee);

        BigDecimal carbonCreditsOut = carbonCreditReserves.multiply(amountAfterFee)
                .divide(baseCurrencyReserves.add(amountAfterFee), 18, RoundingMode.DOWN);

        if (carbonCreditsOut.compareTo(carbonCreditReserves) >= 0) {
            throw new IllegalStateException("Insufficient carbon credit reserves");
        }

        // Note: In a real implementation, base currency would be transferred here
        baseCurrencyReserves = baseCurrencyReserves.add(amountBaseCurrency);
        carbonCreditReserves = carbonCreditReserves.subtract(carbonCreditsOut);
        token.transfer(token.getAdminAddress(), trader, carbonCreditsOut);
    }

    private void checkExchangeRate(BigDecimal minExchangeRate, BigDecimal maxExchangeRate) {
        BigDecimal currentRate = calculateExchangeRate();
        if (currentRate.compareTo(minExchangeRate) < 0 || 
            currentRate.compareTo(maxExchangeRate) > 0) {
            throw new IllegalStateException("Exchange rate out of bounds");
        }
    }

    private BigDecimal calculateExchangeRate() {
        return carbonCreditReserves.multiply(exchangeRateMultiplier)
                .divide(baseCurrencyReserves, 18, RoundingMode.DOWN);
    }

    public BigDecimal getLiquidity(String address) {
        return liquidityProviders.getOrDefault(address, BigDecimal.ZERO);
    }
} 