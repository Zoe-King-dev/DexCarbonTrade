package org.example.model;

import lombok.Data;
import javax.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.ArrayList;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import javax.persistence.NamedEntityGraph;
import javax.persistence.NamedAttributeNode;

@Data
@Entity
@Table(name = "carbon_exchanges")
@NamedEntityGraph(
    name = "exchange.withBalances",
    attributeNodes = @NamedAttributeNode("baseBalances")
)
public class CarbonExchange {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "token_id")
    private CarbonCreditToken token;

    @OneToOne(mappedBy = "exchange", cascade = CascadeType.ALL)
    private LiquidityPool liquidityPool;

    @OneToMany(mappedBy = "exchange", cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @Fetch(FetchMode.JOIN)
    private List<UsdcBalance> baseBalances = new ArrayList<>();

    @Column(name = "carbon_fee_reserves")
    private BigDecimal carbonFeeReserves = BigDecimal.ZERO;

    @Column(name = "base_fee_reserves")
    private BigDecimal baseFeeReserves = BigDecimal.ZERO;

    private static final BigDecimal INITIAL_CCT_AMOUNT = new BigDecimal("5000");
    private static final BigDecimal INITIAL_USDC_AMOUNT = new BigDecimal("5000");
    private static final BigDecimal INITIAL_TOTAL_SHARES = new BigDecimal("100000");

    public CarbonExchange() {
        this.liquidityPool = new LiquidityPool(this);
    }

    public CarbonExchange(CarbonCreditToken token) {
        this();
        this.token = token;
    }

    @PrePersist
    public void prePersist() {
        if (this.liquidityPool == null) {
            this.liquidityPool = new LiquidityPool(this);
        }
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
    }

    public BigDecimal calculateExchangeRate() {
        return liquidityPool.getTotalCarbonReserves().multiply(liquidityPool.getExchangeRateMultiplier())
                .divide(liquidityPool.getTotalBaseReserves(), 18, RoundingMode.DOWN);
    }

    public BigDecimal getLiquidity(String address) {
        return baseBalances.stream()
                .filter(b -> b.getAddress().equals(address))
                .findFirst()
                .map(UsdcBalance::getBalance)
                .orElse(BigDecimal.ZERO);
    }

    public BigDecimal getBaseBalance(String address) {
        System.out.println("DEBUG: Getting USDC balance for address: " + address);
        System.out.println("DEBUG: Number of balances loaded: " + baseBalances.size());
        baseBalances.forEach(b -> System.out.println("DEBUG: Balance for " + b.getAddress() + ": " + b.getBalance()));
        BigDecimal balance = baseBalances.stream()
                .filter(b -> b.getAddress().equals(address))
                .findFirst()
                .map(UsdcBalance::getBalance)
                .orElse(BigDecimal.ZERO);
        System.out.println("DEBUG: Found balance: " + balance);
        return balance;
    }

    public void setBaseBalance(String address, BigDecimal amount) {
        UsdcBalance balance = baseBalances.stream()
                .filter(b -> b.getAddress().equals(address))
                .findFirst()
                .orElseGet(() -> {
                    UsdcBalance newBalance = new UsdcBalance();
                    newBalance.setAddress(address);
                    newBalance.setExchange(this);
                    baseBalances.add(newBalance);
                    return newBalance;
                });
        balance.setBalance(amount);
    }
} 