package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.model.CarbonExchange;
import org.example.model.LiquidityInfo;
import org.example.model.LiquidityPool;
import org.example.repository.CarbonExchangeRepository;
import org.example.repository.LiquidityPoolRepository;
import org.example.repository.LiquidityProviderRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CarbonExchangeService {
    private final CarbonExchangeRepository exchangeRepository;
    private final LiquidityPoolRepository liquidityPoolRepository;
    private final LiquidityProviderRepository liquidityProviderRepository;

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

    @Transactional
    public void addLiquidity(String userId, BigDecimal amountUsdc, BigDecimal maxSlippagePercentage) {
        LiquidityPool pool = getLiquidityPool();
        CarbonExchange exchange = getExchange();

        // 檢查滑點
        validateSlippage(maxSlippagePercentage);

        // 檢查用戶餘額是否足夠
        BigDecimal userUsdcBalance = exchange.getBaseBalance(userId);
        if (userUsdcBalance.compareTo(amountUsdc) < 0) {
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
            pool.setExchange(exchange);
        }

        // Calculate required CCT amount based on current ratio
        BigDecimal currentRate = calculateExchangeRate();
        BigDecimal requiredCct = amountUsdc.multiply(currentRate);

        // 檢查用戶 CCT 餘額是否足夠
        BigDecimal userCctBalance = exchange.getToken().getBalance(userId);
        if (userCctBalance.compareTo(requiredCct) < 0) {
            throw new IllegalStateException("Insufficient CCT balance");
        }

        // 減少用戶餘額
        exchange.setBaseBalance(userId, userUsdcBalance.subtract(amountUsdc));
        exchange.getToken().setBalance(userId, userCctBalance.subtract(requiredCct));

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
        org.example.model.LiquidityProvider provider = new org.example.model.LiquidityProvider();
        provider.setProviderAddress(userId);
        provider.setShares(newShares);
        provider.setPool(pool);
        provider.setExchange(exchange);
        liquidityProviderRepository.save(provider);

        // 保存更新後的 exchange
        exchangeRepository.save(exchange);
    }

    @Transactional
    public void removeLiquidity(String userId, BigDecimal amountUsdc, BigDecimal maxSlippagePercentage) {
        LiquidityPool pool = getLiquidityPool();
        CarbonExchange exchange = getExchange();

        // 檢查滑點
        validateSlippage(maxSlippagePercentage);

        // Calculate user's share percentage
        BigDecimal userShare = pool.getLiquidity(userId);
        if (userShare.compareTo(amountUsdc) < 0) {
            throw new IllegalStateException("Insufficient liquidity");
        }

        BigDecimal sharePercentage = amountUsdc.divide(pool.getTotalBaseReserves(), 18, RoundingMode.HALF_UP);
        
        // Calculate amounts to return
        BigDecimal returnCct = pool.getTotalCarbonReserves().multiply(sharePercentage);
        BigDecimal returnUsdc = amountUsdc;

        // Calculate and add LP fees (3%)
        BigDecimal feeCct = returnCct.multiply(new BigDecimal("0.03"));
        BigDecimal feeUsdc = returnUsdc.multiply(new BigDecimal("0.03"));

        // Update reserves and k
        pool.setTotalCarbonReserves(pool.getTotalCarbonReserves().subtract(returnCct));
        pool.setTotalBaseReserves(pool.getTotalBaseReserves().subtract(returnUsdc));
        pool.setK(pool.getTotalCarbonReserves().multiply(pool.getTotalBaseReserves()));

        // Update LP shares
        BigDecimal removedShares = amountUsdc.multiply(pool.getTotalShares())
                .divide(pool.getTotalBaseReserves(), 18, RoundingMode.HALF_UP);
        pool.setTotalShares(pool.getTotalShares().subtract(removedShares));
        pool.removeLiquidityProvider(userId, removedShares);

        // Update fee reserves
        exchange.setCarbonFeeReserves(exchange.getCarbonFeeReserves().add(feeCct));
        exchange.setBaseFeeReserves(exchange.getBaseFeeReserves().add(feeUsdc));

        liquidityPoolRepository.save(pool);
        exchangeRepository.save(exchange);
    }

    @Transactional
    public void removeAllLiquidity(String userId, BigDecimal maxSlippagePercentage) {
        LiquidityPool pool = getLiquidityPool();
        BigDecimal userLiquidity = pool.getLiquidity(userId);
        removeLiquidity(userId, userLiquidity, maxSlippagePercentage);
    }

    private LiquidityPool getLiquidityPool() {
        return liquidityPoolRepository.findById(4L)
                .orElseThrow(() -> new IllegalStateException("Liquidity pool not found"));
    }

    public CarbonExchange getExchange() {
        return exchangeRepository.findById(1L)
                .orElseThrow(() -> new IllegalStateException("Exchange not found"));
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

    public BigDecimal calculateExchangeRate() {
        LiquidityPool pool = getLiquidityPool();
        if (pool.getTotalBaseReserves().compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return pool.getTotalCarbonReserves()
                .divide(pool.getTotalBaseReserves(), 18, RoundingMode.HALF_UP);
    }

    public BigDecimal getBaseBalance(String address) {
        CarbonExchange exchange = getExchange();
        return exchange.getBaseBalance(address);
    }

    @Transactional
    public void swapCarbonCreditsForBaseCurrency(String trader, BigDecimal amountCarbonCredits, BigDecimal maxExchangeRate) {
        LiquidityPool pool = getLiquidityPool();
        CarbonExchange exchange = getExchange();

        BigDecimal currentRate = calculateExchangeRate();
        if (currentRate.compareTo(maxExchangeRate) > 0) {
            throw new IllegalStateException("Slippage too large");
        }

        BigDecimal baseOut = pool.getTotalBaseReserves().multiply(amountCarbonCredits)
                .divide(pool.getTotalCarbonReserves().add(amountCarbonCredits), 18, RoundingMode.HALF_UP);

        pool.setTotalCarbonReserves(pool.getTotalCarbonReserves().add(amountCarbonCredits));
        pool.setTotalBaseReserves(pool.getTotalBaseReserves().subtract(baseOut));
        liquidityPoolRepository.save(pool);
    }

    @Transactional
    public void swapBaseCurrencyForCarbonCredits(String trader, BigDecimal amountBaseCurrency, BigDecimal maxExchangeRate) {
        LiquidityPool pool = getLiquidityPool();
        CarbonExchange exchange = getExchange();

        BigDecimal currentRate = calculateExchangeRate();
        if (currentRate.compareTo(maxExchangeRate) > 0) {
            throw new IllegalStateException("Slippage too large");
        }

        BigDecimal carbonOut = pool.getTotalCarbonReserves().multiply(amountBaseCurrency)
                .divide(pool.getTotalBaseReserves().add(amountBaseCurrency), 18, RoundingMode.HALF_UP);

        pool.setTotalBaseReserves(pool.getTotalBaseReserves().add(amountBaseCurrency));
        pool.setTotalCarbonReserves(pool.getTotalCarbonReserves().subtract(carbonOut));
        liquidityPoolRepository.save(pool);
    }
} 