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

    @OneToOne(mappedBy = "exchange", cascade = CascadeType.ALL)
    private LiquidityPool liquidityPool;

    @ElementCollection
    @CollectionTable(name = "liquidity_providers", joinColumns = @JoinColumn(name = "exchange_id"))
    @MapKeyColumn(name = "provider_address")
    @Column(name = "shares")
    private Map<String, BigDecimal> liquidityProviders = new HashMap<>();

    private static final BigDecimal INITIAL_CCT_AMOUNT = new BigDecimal("5000");
    private static final BigDecimal INITIAL_USDC_AMOUNT = new BigDecimal("5000");
    private static final BigDecimal INITIAL_TOTAL_SHARES = new BigDecimal("100000");

    public CarbonExchange() {
        this.liquidityPool = new LiquidityPool(this);
        this.liquidityPool.setTotalCarbonReserves(BigDecimal.ZERO);
        this.liquidityPool.setTotalBaseReserves(BigDecimal.ZERO);
        this.liquidityPool.setTotalShares(BigDecimal.ZERO);
        this.liquidityPool.setK(BigDecimal.ZERO);
        this.liquidityPool.setExchangeRateMultiplier(BigDecimal.valueOf(10).pow(5));
    }

    public CarbonExchange(CarbonCreditToken token) {
        this();
        this.token = token;
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
        if (liquidityPool.getTotalCarbonReserves().equals(BigDecimal.ZERO) && 
            liquidityPool.getTotalBaseReserves().equals(BigDecimal.ZERO)) {
            // 從用戶餘額中扣除 CCT
            token.transfer(provider, token.getAddress(), INITIAL_CCT_AMOUNT);
            
            // 設置初始儲備
            liquidityPool.setTotalCarbonReserves(INITIAL_CCT_AMOUNT);
            liquidityPool.setTotalBaseReserves(INITIAL_USDC_AMOUNT);
            
            // 計算並設置 k 值
            liquidityPool.setK(INITIAL_CCT_AMOUNT.multiply(INITIAL_USDC_AMOUNT));
            
            // 分配初始 LP 份額
            liquidityPool.setTotalShares(INITIAL_TOTAL_SHARES);
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
        
        BigDecimal newShares = amountBaseCurrency.multiply(liquidityPool.getTotalShares())
                .divide(liquidityPool.getTotalBaseReserves(), 18, BigDecimal.ROUND_DOWN);
        
        liquidityPool.setTotalCarbonReserves(liquidityPool.getTotalCarbonReserves().add(amountCarbonCredits));
        liquidityPool.setTotalBaseReserves(liquidityPool.getTotalBaseReserves().add(amountBaseCurrency));
        liquidityPool.setTotalShares(liquidityPool.getTotalShares().add(newShares));
        
        liquidityProviders.merge(provider, newShares, BigDecimal::add);
    }

    public BigDecimal calculateExchangeRate() {
        return liquidityPool.getTotalCarbonReserves().multiply(liquidityPool.getExchangeRateMultiplier())
                .divide(liquidityPool.getTotalBaseReserves(), 18, RoundingMode.DOWN);
    }

    public BigDecimal getLiquidity(String address) {
        return liquidityProviders.getOrDefault(address, BigDecimal.ZERO);
    }
} 