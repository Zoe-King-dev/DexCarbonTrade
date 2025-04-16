package org.example.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import javax.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

@Data
@NoArgsConstructor
@Entity
@Table(name = "carbon_exchanges")
public class CarbonExchange {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "token_id")
    private CarbonCreditToken token;

    private BigDecimal carbonCreditReserves = BigDecimal.ZERO;
    private BigDecimal baseCurrencyReserves = BigDecimal.ZERO;
    private BigDecimal totalShares = BigDecimal.ZERO;
    private BigDecimal k = BigDecimal.ZERO;
    private BigDecimal exchangeRateMultiplier = BigDecimal.valueOf(10).pow(5);

    @ElementCollection
    @CollectionTable(name = "liquidity_providers", joinColumns = @JoinColumn(name = "exchange_id"))
    @MapKeyColumn(name = "provider_address")
    @Column(name = "shares")
    private Map<String, BigDecimal> liquidityProviders = new HashMap<>();

    private static final BigDecimal INITIAL_CCT_AMOUNT = new BigDecimal("5000");
    private static final BigDecimal INITIAL_USDC_AMOUNT = new BigDecimal("5000");
    private static final BigDecimal INITIAL_TOTAL_SHARES = new BigDecimal("100000");

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
        // 檢查用戶餘額
        if (token.getBalance(provider).compareTo(INITIAL_CCT_AMOUNT) < 0) {
            throw new IllegalStateException("Insufficient CCT balance for initialization");
        }

        // 檢查基礎貨幣餘額
        if (amountBaseCurrency.compareTo(INITIAL_USDC_AMOUNT) < 0) {
            throw new IllegalStateException("Insufficient USDC amount for initialization");
        }

        // 如果是空池，進行初始化
        if (carbonCreditReserves.equals(BigDecimal.ZERO) && baseCurrencyReserves.equals(BigDecimal.ZERO)) {
            // 從用戶餘額中扣除 CCT
            token.transfer(provider, token.getAddress(), INITIAL_CCT_AMOUNT);
            
            // 設置初始儲備
            carbonCreditReserves = INITIAL_CCT_AMOUNT;
            baseCurrencyReserves = INITIAL_USDC_AMOUNT;
            
            // 計算並設置 k 值
            k = carbonCreditReserves.multiply(baseCurrencyReserves);
            
            // 分配初始 LP 份額
            totalShares = INITIAL_TOTAL_SHARES;
            liquidityProviders.put(provider, INITIAL_TOTAL_SHARES);
            
            return;
        }

        // 現有的添加流動性邏輯
        BigDecimal currentExchangeRate = calculateExchangeRate();
        if (currentExchangeRate.compareTo(minExchangeRate) < 0 || 
            currentExchangeRate.compareTo(maxExchangeRate) > 0) {
            throw new IllegalStateException("Exchange rate out of range");
        }

        BigDecimal amountCarbonCredits = amountBaseCurrency.multiply(currentExchangeRate);
        if (token.getBalance(provider).compareTo(amountCarbonCredits) < 0) {
            throw new IllegalStateException("Insufficient carbon credit balance");
        }

        token.transfer(provider, token.getAddress(), amountCarbonCredits);
        
        BigDecimal newShares = amountBaseCurrency.multiply(totalShares)
                .divide(baseCurrencyReserves, 18, BigDecimal.ROUND_DOWN);
        
        carbonCreditReserves = carbonCreditReserves.add(amountCarbonCredits);
        baseCurrencyReserves = baseCurrencyReserves.add(amountBaseCurrency);
        totalShares = totalShares.add(newShares);
        
        liquidityProviders.merge(provider, newShares, BigDecimal::add);
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