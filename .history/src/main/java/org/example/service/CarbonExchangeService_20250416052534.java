package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.model.*;
import org.example.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CarbonExchangeService {
    private static final Logger logger = LoggerFactory.getLogger(CarbonExchangeService.class);
    
    private final LiquidityPoolRepository liquidityPoolRepository;
    private final LiquidityProviderRepository liquidityProviderRepository;
    private final UsdcBalanceRepository usdcBalanceRepository;
    private final CarbonExchangeRepository carbonExchangeRepository;
    private final CarbonCreditTokenRepository carbonCreditTokenRepository;
    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    @Transactional
    public String addLiquidity(Map<String, String> request) {
        try {
            String userId = request.get("userId");
            BigDecimal amountUsdc = new BigDecimal(request.get("amountUsdc"));
            BigDecimal maxSlippagePercentage = new BigDecimal(request.get("maxSlippagePercentage"));
            
            logger.info("開始添加流動性 - userId: {}, amountUsdc: {}, maxSlippagePercentage: {}", 
                userId, amountUsdc, maxSlippagePercentage);
            
            // 獲取用戶
            Optional<User> userOpt = userRepository.findByAddress(userId);
            if (!userOpt.isPresent()) {
                logger.error("用戶不存在: {}", userId);
                return "用戶不存在";
            }
            User user = userOpt.get();
            logger.info("找到用戶: {}", user);
            
            if (user.getExchangeId() == null || user.getExchangeId() == 0) {
                logger.error("用戶的 exchangeId 為空或 0: {}", user);
                return "用戶未關聯交易所";
            }
            
            // 獲取交易所
            CarbonExchange exchange = carbonExchangeRepository.findById(user.getExchangeId())
                .orElseThrow(() -> {
                    logger.error("找不到交易所，exchangeId: {}", user.getExchangeId());
                    return new RuntimeException("找不到交易所");
                });
            logger.info("找到交易所: {}", exchange.getId());
            
            // 獲取流動性池
            LiquidityPool pool = liquidityPoolRepository.findById(exchange.getId())
                .orElseThrow(() -> {
                    logger.error("找不到流動性池，exchangeId: {}", exchange.getId());
                    return new RuntimeException("找不到流動性池");
                });
            logger.info("找到流動性池: {}", pool.getId());
            
            // 檢查用戶餘額
            UsdcBalance usdcBalance = usdcBalanceRepository.findByAddress(userId)
                .orElseThrow(() -> {
                    logger.error("找不到 USDC 餘額，userId: {}", userId);
                    return new RuntimeException("找不到 USDC 餘額");
                });
            logger.info("用戶 USDC 餘額: {}", usdcBalance.getBalance());
            
            if (usdcBalance.getBalance().compareTo(amountUsdc) < 0) {
                logger.error("USDC 餘額不足，當前餘額: {}, 需要: {}", 
                    usdcBalance.getBalance(), amountUsdc);
                return "USDC 餘額不足";
            }
            
            // 計算 CCT 數量
            BigDecimal cctAmount = amountUsdc.multiply(pool.getCctReserves())
                .divide(pool.getUsdcReserves(), 18, RoundingMode.DOWN);
            logger.info("計算出的 CCT 數量: {}", cctAmount);
            
            // 檢查用戶 CCT 餘額
            CarbonCreditToken cct = carbonCreditTokenRepository.findById(user.getExchangeId())
                .orElseThrow(() -> {
                    logger.error("找不到 CCT，exchangeId: {}", user.getExchangeId());
                    return new RuntimeException("找不到 CCT");
                });
            logger.info("用戶 CCT 餘額: {}", cct.getBalance(userId));
            
            if (cct.getBalance(userId).compareTo(cctAmount) < 0) {
                logger.error("CCT 餘額不足，當前餘額: {}, 需要: {}", 
                    cct.getBalance(userId), cctAmount);
                return "CCT 餘額不足";
            }
            
            // 計算滑點
            BigDecimal actualSlippage = cctAmount.multiply(new BigDecimal("100"))
                .divide(pool.getCctReserves(), 2, RoundingMode.DOWN);
            logger.info("實際滑點: {}%", actualSlippage);
            
            if (actualSlippage.compareTo(maxSlippagePercentage) > 0) {
                logger.error("滑點過大，實際滑點: {}%, 最大允許滑點: {}%", 
                    actualSlippage, maxSlippagePercentage);
                return "滑點過大";
            }
            
            // 更新用戶餘額
            usdcBalance.setBalance(usdcBalance.getBalance().subtract(amountUsdc));
            usdcBalanceRepository.save(usdcBalance);
            logger.info("更新後 USDC 餘額: {}", usdcBalance.getBalance());
            
            cct.setBalance(userId, cct.getBalance(userId).subtract(cctAmount));
            carbonCreditTokenRepository.save(cct);
            logger.info("更新後 CCT 餘額: {}", cct.getBalance(userId));
            
            // 更新流動性池
            pool.setUsdcReserves(pool.getUsdcReserves().add(amountUsdc));
            pool.setCctReserves(pool.getCctReserves().add(cctAmount));
            liquidityPoolRepository.save(pool);
            logger.info("更新後流動性池 - USDC 儲備: {}, CCT 儲備: {}", 
                pool.getUsdcReserves(), pool.getCctReserves());
            
            // 計算 LP 份額
            BigDecimal totalShares = pool.getTotalShares();
            BigDecimal newShares = totalShares.compareTo(BigDecimal.ZERO) == 0 ? 
                amountUsdc : 
                amountUsdc.multiply(totalShares).divide(pool.getUsdcReserves(), 18, RoundingMode.DOWN);
            logger.info("新增 LP 份額: {}", newShares);
            
            // 更新流動性提供者
            LiquidityProviderId providerId = new LiquidityProviderId(exchange.getId(), userId, pool.getId());
            LiquidityProvider provider = liquidityProviderRepository.findById(providerId)
                .orElse(new LiquidityProvider());
            provider.setExchange(exchange);
            provider.setProviderAddress(userId);
            provider.setPool(pool);
            provider.setShares(provider.getShares().add(newShares));
            liquidityProviderRepository.save(provider);
            logger.info("更新後流動性提供者份額: {}", provider.getShares());
            
            // 更新總份額
            pool.setTotalShares(totalShares.add(newShares));
            liquidityPoolRepository.save(pool);
            logger.info("更新後總份額: {}", pool.getTotalShares());
            
            // 記錄交易
            Transaction transaction = new Transaction();
            transaction.setFromAddress(userId);
            transaction.setToAddress("0x0000000000000000000000000000000000000000");
            transaction.setAmount(amountUsdc);
            transaction.setTokenAddress("USDC");
            transaction.setType("ADD_LIQUIDITY");
            transactionRepository.save(transaction);
            logger.info("交易記錄已保存");
            
            return "添加流動性成功";
        } catch (Exception e) {
            logger.error("添加流動性失敗", e);
            return "添加流動性失敗: " + e.getMessage();
        }
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

    public LiquidityPool getLiquidityPool() {
        List<LiquidityPool> pools = liquidityPoolRepository.findAll();
        if (pools.isEmpty()) {
            // 如果沒有流動性池，創建一個新的
            LiquidityPool newPool = new LiquidityPool();
            newPool.setUsdcReserves(BigDecimal.ZERO);
            newPool.setCctReserves(BigDecimal.ZERO);
            newPool.setTotalShares(BigDecimal.ZERO);
            newPool.setExchangeRateMultiplier(BigDecimal.ONE);
            newPool.setK(BigDecimal.ZERO);
            return liquidityPoolRepository.save(newPool);
        }
        return pools.get(0);
    }

    public BigDecimal calculateExchangeRate() {
        LiquidityPool pool = getLiquidityPool();
        if (pool == null || pool.getUsdcReserves() == null || pool.getCctReserves() == null) {
            return BigDecimal.ZERO;
        }
        if (pool.getCctReserves().compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return pool.getUsdcReserves().divide(pool.getCctReserves(), 18, RoundingMode.HALF_UP);
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
        info.setTotalCarbonReserves(pool.getCctReserves());
        info.setTotalBaseReserves(pool.getUsdcReserves());
        info.setUserSharePercentage(pool.getUserSharePercentage(address));
        info.setCurrentExchangeRate(calculateExchangeRate());
        return info;
    }

    public void removeLiquidity(String userId, BigDecimal amountUsdc, BigDecimal maxSlippagePercentage) {
        LiquidityPool pool = liquidityPoolRepository.findAll().get(0);
        CarbonExchange exchange = carbonExchangeRepository.findById(pool.getId())
            .orElseThrow(() -> new RuntimeException("Exchange not found"));
        LiquidityProvider provider = liquidityProviderRepository.findById(new LiquidityProviderId(exchange.getId(), userId, pool.getId()))
                .orElseThrow(() -> new RuntimeException("No liquidity provided by this user"));

        BigDecimal sharesToRemove = amountUsdc.multiply(provider.getShares())
                .divide(pool.getUsdcReserves(), 18, RoundingMode.DOWN);

        if (sharesToRemove.compareTo(provider.getShares()) > 0) {
            throw new RuntimeException("Insufficient shares");
        }

        BigDecimal carbonAmount = sharesToRemove.multiply(pool.getCctReserves())
                .divide(pool.getTotalShares(), 18, RoundingMode.DOWN);
        BigDecimal usdcAmount = sharesToRemove.multiply(pool.getUsdcReserves())
                .divide(pool.getTotalShares(), 18, RoundingMode.DOWN);

        pool.setCctReserves(pool.getCctReserves().subtract(carbonAmount));
        pool.setUsdcReserves(pool.getUsdcReserves().subtract(usdcAmount));
        pool.setTotalShares(pool.getTotalShares().subtract(sharesToRemove));
        provider.setShares(provider.getShares().subtract(sharesToRemove));

        liquidityPoolRepository.save(pool);
        liquidityProviderRepository.save(provider);
    }

    public void removeAllLiquidity(String userId, BigDecimal maxSlippagePercentage) {
        LiquidityPool pool = liquidityPoolRepository.findAll().get(0);
        CarbonExchange exchange = carbonExchangeRepository.findById(pool.getId())
            .orElseThrow(() -> new RuntimeException("Exchange not found"));
        LiquidityProvider provider = liquidityProviderRepository.findById(new LiquidityProviderId(exchange.getId(), userId, pool.getId()))
                .orElseThrow(() -> new RuntimeException("No liquidity provided by this user"));

        BigDecimal carbonAmount = provider.getShares().multiply(pool.getCctReserves())
                .divide(pool.getTotalShares(), 18, RoundingMode.DOWN);
        BigDecimal usdcAmount = provider.getShares().multiply(pool.getUsdcReserves())
                .divide(pool.getTotalShares(), 18, RoundingMode.DOWN);

        pool.setCctReserves(pool.getCctReserves().subtract(carbonAmount));
        pool.setUsdcReserves(pool.getUsdcReserves().subtract(usdcAmount));
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
        pool.setCctReserves(pool.getCctReserves().add(amountCarbonCredits));
        pool.setUsdcReserves(pool.getUsdcReserves().subtract(usdcAmount));

        liquidityPoolRepository.save(pool);
    }

    public void swapBaseCurrencyForCarbonCredits(String trader, BigDecimal amountBaseCurrency, BigDecimal maxExchangeRate) {
        LiquidityPool pool = liquidityPoolRepository.findAll().get(0);
        BigDecimal exchangeRate = pool.calculateExchangeRate();
        
        if (exchangeRate.compareTo(maxExchangeRate) > 0) {
            throw new RuntimeException("Exchange rate exceeds maximum allowed");
        }

        BigDecimal carbonAmount = amountBaseCurrency.divide(exchangeRate, 18, RoundingMode.DOWN);
        pool.setUsdcReserves(pool.getUsdcReserves().add(amountBaseCurrency));
        pool.setCctReserves(pool.getCctReserves().subtract(carbonAmount));

        liquidityPoolRepository.save(pool);
    }

    public void setBaseBalance(String address, BigDecimal newBalance) {
        UsdcBalance balance = usdcBalanceRepository.findByAddress(address)
                .orElseThrow(() -> new IllegalStateException("USDC balance not found for address: " + address));
        balance.setBalance(newBalance);
        usdcBalanceRepository.save(balance);
    }
} 