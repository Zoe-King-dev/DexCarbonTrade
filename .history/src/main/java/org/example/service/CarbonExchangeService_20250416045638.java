package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.model.*;
import org.example.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CarbonExchangeService {
    private final LiquidityPoolRepository liquidityPoolRepository;
    private final LiquidityProviderRepository liquidityProviderRepository;
    private final UsdcBalanceRepository usdcBalanceRepository;
    private final CarbonExchangeRepository carbonExchangeRepository;

    @Transactional
    public void addLiquidity(String userId, BigDecimal amountUsdc, BigDecimal maxSlippagePercentage) {
        System.out.println("DEBUG: Starting addLiquidity for user: " + userId);
        System.out.println("DEBUG: Amount USDC: " + amountUsdc);
        System.out.println("DEBUG: Max Slippage: " + maxSlippagePercentage);

        // 獲取或創建 CarbonExchange
        CarbonExchange exchange = carbonExchangeRepository.findById(1L)
                .orElseGet(() -> {
                    System.out.println("DEBUG: Creating new CarbonExchange");
                    CarbonExchange newExchange = new CarbonExchange();
                    newExchange.setId(1L);
                    return carbonExchangeRepository.save(newExchange);
                });

        // 獲取或創建 LiquidityPool
        LiquidityPool pool = liquidityPoolRepository.findAll().stream()
                .findFirst()
                .orElseGet(() -> {
                    System.out.println("DEBUG: Creating new LiquidityPool");
                    LiquidityPool newPool = new LiquidityPool();
                    newPool.setExchange(exchange);
                    return liquidityPoolRepository.save(newPool);
                });

        System.out.println("DEBUG: Found/created pool with ID: " + pool.getId());
        System.out.println("DEBUG: Pool exchange ID: " + pool.getExchange().getId());

        // 檢查滑點
        validateSlippage(maxSlippagePercentage);

        // 檢查用戶餘額是否足夠
        BigDecimal userUsdcBalance = getBaseBalance(userId);
        System.out.println("DEBUG: User USDC balance: " + userUsdcBalance);

        if (userUsdcBalance.compareTo(amountUsdc) < 0) {
            System.out.println("ERROR: Insufficient USDC balance");
            throw new IllegalStateException("Insufficient USDC balance");
        }

        // 計算新的份額
        BigDecimal newShares;
        if (pool.getTotalShares().compareTo(BigDecimal.ZERO) == 0) {
            System.out.println("DEBUG: Initializing pool with first liquidity");
            newShares = amountUsdc;
            pool.setTotalCarbonReserves(amountUsdc);
            pool.setTotalBaseReserves(amountUsdc);
        } else {
            System.out.println("DEBUG: Adding to existing pool");
            newShares = amountUsdc.multiply(pool.getTotalShares())
                    .divide(pool.getTotalBaseReserves(), 18, RoundingMode.DOWN);
            pool.setTotalCarbonReserves(pool.getTotalCarbonReserves().add(amountUsdc));
            pool.setTotalBaseReserves(pool.getTotalBaseReserves().add(amountUsdc));
        }

        System.out.println("DEBUG: New shares to be added: " + newShares);

        // 更新池子
        pool.setTotalShares(pool.getTotalShares().add(newShares));
        pool = liquidityPoolRepository.save(pool);
        System.out.println("DEBUG: Pool updated with new total shares: " + pool.getTotalShares());

        // 創建或更新流動性提供者
        LiquidityProvider provider = liquidityProviderRepository.findById(new LiquidityProviderId(pool.getExchange().getId(), userId))
                .orElseGet(() -> {
                    System.out.println("DEBUG: Creating new LiquidityProvider");
                    LiquidityProvider newProvider = new LiquidityProvider();
                    newProvider.setProviderAddress(userId);
                    newProvider.setPool(pool);
                    newProvider.setShares(BigDecimal.ZERO);
                    return newProvider;
                });

        provider.setShares(provider.getShares().add(newShares));
        liquidityProviderRepository.save(provider);
        System.out.println("DEBUG: Provider updated with new shares: " + provider.getShares());

        // 更新用戶餘額
        setBaseBalance(userId, userUsdcBalance.subtract(amountUsdc));
        System.out.println("DEBUG: User USDC balance updated");
    }

    private void validateSlippage(BigDecimal maxSlippagePercentage) {
        BigDecimal currentRate = calculateExchangeRate();
        BigDecimal slippageFactor = BigDecimal.ONE.subtract(
            maxSlippagePercentage.divide(BigDecimal.valueOf(100), 18, RoundingMode.HALF_UP)
        );
        
        BigDecimal minRate = currentRate.multiply(slippageFactor);
        BigDecimal maxRate = currentRate.divide(slippageFactor, 18, RoundingMode.HALF_UP);
        
        // 獲取實際執行時的匯率
        BigDecimal executionRate = calculateExchangeRate();
        
        if (executionRate.compareTo(minRate) < 0 || executionRate.compareTo(maxRate) > 0) {
            throw new IllegalStateException(
                String.format("Slippage too large. Current rate: %s, Allowed range: %s - %s",
                    executionRate, minRate, maxRate)
            );
        }
    }

    private LiquidityPool getLiquidityPool() {
        return liquidityPoolRepository.findById(4L)
                .orElseThrow(() -> new IllegalStateException("Liquidity pool not found"));
    }

    public BigDecimal calculateExchangeRate() {
        LiquidityPool pool = getLiquidityPool();
        if (pool.getTotalBaseReserves().compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return pool.getTotalCarbonReserves()
                .multiply(pool.getExchangeRateMultiplier())
                .divide(pool.getTotalBaseReserves(), 18, RoundingMode.HALF_UP);
    }

    public BigDecimal getBaseBalance(String address) {
        System.out.println("DEBUG: Getting USDC balance for address: " + address);
        System.out.println("DEBUG: Querying usdc_balances table directly");
        
        UsdcBalance balance = usdcBalanceRepository.findByAddress(address)
                .orElseThrow(() -> new IllegalStateException("USDC balance not found for address: " + address));
        
        System.out.println("DEBUG: Found balance: " + balance.getBalance());
        return balance.getBalance();
    }

    public LiquidityInfo getLiquidity(String address) {
        LiquidityPool pool = getLiquidityPool();
        LiquidityInfo info = new LiquidityInfo();
        info.setUserLiquidity(pool.getLiquidity(address));
        info.setTotalCarbonReserves(pool.getTotalCarbonReserves());
        info.setTotalBaseReserves(pool.getTotalBaseReserves());
        info.setUserSharePercentage(pool.getUserSharePercentage(address));
        info.setCurrentExchangeRate(calculateExchangeRate());
        return info;
    }

    public void removeLiquidity(String userId, BigDecimal amountUsdc, BigDecimal maxSlippagePercentage) {
        LiquidityPool pool = liquidityPoolRepository.findAll().get(0);
        LiquidityProvider provider = liquidityProviderRepository.findById(new LiquidityProviderId(pool.getExchangeId(), userId))
                .orElseThrow(() -> new RuntimeException("No liquidity provided by this user"));

        BigDecimal sharesToRemove = amountUsdc.multiply(provider.getShares())
                .divide(pool.getTotalBaseReserves(), 18, RoundingMode.DOWN);

        if (sharesToRemove.compareTo(provider.getShares()) > 0) {
            throw new RuntimeException("Insufficient shares");
        }

        BigDecimal carbonAmount = sharesToRemove.multiply(pool.getTotalCarbonReserves())
                .divide(pool.getTotalShares(), 18, RoundingMode.DOWN);
        BigDecimal usdcAmount = sharesToRemove.multiply(pool.getTotalBaseReserves())
                .divide(pool.getTotalShares(), 18, RoundingMode.DOWN);

        pool.setTotalCarbonReserves(pool.getTotalCarbonReserves().subtract(carbonAmount));
        pool.setTotalBaseReserves(pool.getTotalBaseReserves().subtract(usdcAmount));
        pool.setTotalShares(pool.getTotalShares().subtract(sharesToRemove));
        provider.setShares(provider.getShares().subtract(sharesToRemove));

        liquidityPoolRepository.save(pool);
        liquidityProviderRepository.save(provider);
    }

    public void removeAllLiquidity(String userId, BigDecimal maxSlippagePercentage) {
        LiquidityPool pool = liquidityPoolRepository.findAll().get(0);
        LiquidityProvider provider = liquidityProviderRepository.findById(new LiquidityProviderId(pool.getExchangeId(), userId))
                .orElseThrow(() -> new RuntimeException("No liquidity provided by this user"));

        BigDecimal carbonAmount = provider.getShares().multiply(pool.getTotalCarbonReserves())
                .divide(pool.getTotalShares(), 18, RoundingMode.DOWN);
        BigDecimal usdcAmount = provider.getShares().multiply(pool.getTotalBaseReserves())
                .divide(pool.getTotalShares(), 18, RoundingMode.DOWN);

        pool.setTotalCarbonReserves(pool.getTotalCarbonReserves().subtract(carbonAmount));
        pool.setTotalBaseReserves(pool.getTotalBaseReserves().subtract(usdcAmount));
        pool.setTotalShares(pool.getTotalShares().subtract(provider.getShares()));
        provider.setShares(BigDecimal.ZERO);

        liquidityPoolRepository.save(pool);
        liquidityProviderRepository.save(provider);
    }

    public void swapCarbonCreditsForBaseCurrency(String trader, BigDecimal amountCarbonCredits, BigDecimal maxExchangeRate) {
        LiquidityPool pool = liquidityPoolRepository.findAll().get(0);
        BigDecimal exchangeRate = pool.calculateExchangeRate();
        
        if (exchangeRate.compareTo(maxExchangeRate) > 0) {
            throw new RuntimeException("Exchange rate exceeds maximum allowed");
        }

        BigDecimal usdcAmount = amountCarbonCredits.multiply(exchangeRate);
        pool.setTotalCarbonReserves(pool.getTotalCarbonReserves().add(amountCarbonCredits));
        pool.setTotalBaseReserves(pool.getTotalBaseReserves().subtract(usdcAmount));

        liquidityPoolRepository.save(pool);
    }

    public void swapBaseCurrencyForCarbonCredits(String trader, BigDecimal amountBaseCurrency, BigDecimal maxExchangeRate) {
        LiquidityPool pool = liquidityPoolRepository.findAll().get(0);
        BigDecimal exchangeRate = pool.calculateExchangeRate();
        
        if (exchangeRate.compareTo(maxExchangeRate) > 0) {
            throw new RuntimeException("Exchange rate exceeds maximum allowed");
        }

        BigDecimal carbonAmount = amountBaseCurrency.divide(exchangeRate, 18, RoundingMode.DOWN);
        pool.setTotalBaseReserves(pool.getTotalBaseReserves().add(amountBaseCurrency));
        pool.setTotalCarbonReserves(pool.getTotalCarbonReserves().subtract(carbonAmount));

        liquidityPoolRepository.save(pool);
    }

    public void setBaseBalance(String address, BigDecimal newBalance) {
        UsdcBalance balance = usdcBalanceRepository.findByAddress(address)
                .orElseThrow(() -> new IllegalStateException("USDC balance not found for address: " + address));
        balance.setBalance(newBalance);
        usdcBalanceRepository.save(balance);
    }
} 