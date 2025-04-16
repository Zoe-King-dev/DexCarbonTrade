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

    @Transactional
    public void addLiquidity(String userId, BigDecimal amountUsdc, BigDecimal maxSlippagePercentage) {
        LiquidityPool pool = getLiquidityPool();

        // 檢查滑點
        validateSlippage(maxSlippagePercentage);

        // 檢查用戶餘額是否足夠
        BigDecimal userUsdcBalance = getBaseBalance(userId);
        System.out.println("DEBUG: Checking USDC balance for user " + userId);
        System.out.println("DEBUG: Current USDC balance: " + userUsdcBalance);
        System.out.println("DEBUG: Required USDC amount: " + amountUsdc);

        if (userUsdcBalance.compareTo(amountUsdc) < 0) {
            System.out.println("ERROR: Insufficient USDC balance for user " + userId);
            System.out.println("ERROR: Current balance: " + userUsdcBalance);
            System.out.println("ERROR: Required amount: " + amountUsdc);
            throw new IllegalStateException("Insufficient USDC balance");
        }

        // Check if pool is empty
        if (pool.getTotalBaseReserves().compareTo(BigDecimal.ZERO) == 0) {
            // Initialize pool with 5000 CCT and 5000 USDC
            pool.setTotalCarbonReserves(new BigDecimal("5000"));
            pool.setTotalBaseReserves(new BigDecimal("5000"));
            pool.setK(new BigDecimal("25000000"));
            pool.setTotalShares(new BigDecimal("100000"));
            pool.setExchangeRateMultiplier(BigDecimal.ONE);
        }

        // Calculate required CCT amount based on current ratio
        BigDecimal currentRate = calculateExchangeRate();
        BigDecimal requiredCct = amountUsdc.multiply(currentRate);

        // Update reserves and k
        pool.setTotalCarbonReserves(pool.getTotalCarbonReserves().add(requiredCct));
        pool.setTotalBaseReserves(pool.getTotalBaseReserves().add(amountUsdc));
        pool.setK(pool.getTotalCarbonReserves().multiply(pool.getTotalBaseReserves()));

        // Calculate LP shares
        BigDecimal newShares = amountUsdc.multiply(pool.getTotalShares())
                .divide(pool.getTotalBaseReserves(), 18, RoundingMode.HALF_UP);
        pool.setTotalShares(pool.getTotalShares().add(newShares));
        
        // Save the pool first
        liquidityPoolRepository.save(pool);
        
        // Create and save the liquidity provider directly
        LiquidityProvider provider = new LiquidityProvider();
        provider.setProviderAddress(userId);
        provider.setShares(newShares);
        provider.setPool(pool);
        liquidityProviderRepository.save(provider);
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
        LiquidityProvider provider = liquidityProviderRepository.findById(new LiquidityProviderId(pool.getExchange().getId(), userId))
                .orElseThrow(() -> new RuntimeException("No liquidity provided by this user"));

        BigDecimal sharesToRemove = amountUsdc.multiply(provider.getShares())
                .divide(pool.getBaseReserves(), 18, RoundingMode.DOWN);

        if (sharesToRemove.compareTo(provider.getShares()) > 0) {
            throw new RuntimeException("Insufficient shares");
        }

        BigDecimal carbonAmount = sharesToRemove.multiply(pool.getCarbonReserves())
                .divide(pool.getTotalShares(), 18, RoundingMode.DOWN);
        BigDecimal usdcAmount = sharesToRemove.multiply(pool.getBaseReserves())
                .divide(pool.getTotalShares(), 18, RoundingMode.DOWN);

        pool.setCarbonReserves(pool.getCarbonReserves().subtract(carbonAmount));
        pool.setBaseReserves(pool.getBaseReserves().subtract(usdcAmount));
        pool.setTotalShares(pool.getTotalShares().subtract(sharesToRemove));
        provider.setShares(provider.getShares().subtract(sharesToRemove));

        liquidityPoolRepository.save(pool);
        liquidityProviderRepository.save(provider);
    }

    public void removeAllLiquidity(String userId, BigDecimal maxSlippagePercentage) {
        LiquidityPool pool = liquidityPoolRepository.findAll().get(0);
        LiquidityProvider provider = liquidityProviderRepository.findById(new LiquidityProviderId(pool.getExchange().getId(), userId))
                .orElseThrow(() -> new RuntimeException("No liquidity provided by this user"));

        BigDecimal carbonAmount = provider.getShares().multiply(pool.getCarbonReserves())
                .divide(pool.getTotalShares(), 18, RoundingMode.DOWN);
        BigDecimal usdcAmount = provider.getShares().multiply(pool.getBaseReserves())
                .divide(pool.getTotalShares(), 18, RoundingMode.DOWN);

        pool.setCarbonReserves(pool.getCarbonReserves().subtract(carbonAmount));
        pool.setBaseReserves(pool.getBaseReserves().subtract(usdcAmount));
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
        pool.setCarbonReserves(pool.getCarbonReserves().add(amountCarbonCredits));
        pool.setBaseReserves(pool.getBaseReserves().subtract(usdcAmount));

        liquidityPoolRepository.save(pool);
    }

    public void swapBaseCurrencyForCarbonCredits(String trader, BigDecimal amountBaseCurrency, BigDecimal maxExchangeRate) {
        LiquidityPool pool = liquidityPoolRepository.findAll().get(0);
        BigDecimal exchangeRate = pool.calculateExchangeRate();
        
        if (exchangeRate.compareTo(maxExchangeRate) > 0) {
            throw new RuntimeException("Exchange rate exceeds maximum allowed");
        }

        BigDecimal carbonAmount = amountBaseCurrency.divide(exchangeRate, 18, RoundingMode.DOWN);
        pool.setBaseReserves(pool.getBaseReserves().add(amountBaseCurrency));
        pool.setCarbonReserves(pool.getCarbonReserves().subtract(carbonAmount));

        liquidityPoolRepository.save(pool);
    }
} 