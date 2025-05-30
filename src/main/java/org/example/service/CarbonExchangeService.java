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
import java.util.HashMap;

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
            
            // 獲取用戶 USDC 餘額
            USDCBalance usdcBalance = usdcBalanceRepository.findByAddress(userId)
                .orElseThrow(() -> {
                    logger.error("找不到 USDC 餘額，userId: {}", userId);
                    return new RuntimeException("找不到 USDC 餘額");
                });
            logger.info("用戶 USDC 餘額: {}", usdcBalance.getBalance());
            
            // 檢查餘額是否足夠
            if (usdcBalance.getBalance().compareTo(amountUsdc) < 0) {
                logger.error("USDC 餘額不足，當前餘額: {}, 需要: {}", 
                    usdcBalance.getBalance(), amountUsdc);
                return "USDC 餘額不足";
            }
            
            // 獲取流動性池
            LiquidityPool pool = getLiquidityPool();
            logger.info("當前流動性池狀態 - USDC: {}, CCT: {}, K: {}", 
                pool.getUsdcReserves(), pool.getCctReserves(), pool.getK());
            
            // 計算新的 K 值
            BigDecimal newUsdcReserves = pool.getUsdcReserves().add(amountUsdc);
            BigDecimal newK = newUsdcReserves.multiply(pool.getCctReserves());
            logger.info("新的 K 值: {}", newK);
            
            // 更新用戶 USDC 餘額
            usdcBalance.setBalance(usdcBalance.getBalance().subtract(amountUsdc));
            usdcBalanceRepository.save(usdcBalance);
            logger.info("更新後用戶 USDC 餘額: {}", usdcBalance.getBalance());
            
            // 更新流動性池
            pool.setUsdcReserves(newUsdcReserves);
            pool.setK(newK);
            liquidityPoolRepository.save(pool);
            logger.info("更新後流動性池狀態 - USDC: {}, CCT: {}, K: {}", 
                pool.getUsdcReserves(), pool.getCctReserves(), pool.getK());
            
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
        //logger.info("開始獲取流動性池");
        try {
            List<LiquidityPool> pools = liquidityPoolRepository.findAll();
            //logger.info("數據庫中的流動性池數量: {}", pools.size());
            
            if (pools.isEmpty()) {
                logger.error("未找到流動性池");
                throw new RuntimeException("No liquidity pool found");
            }
            
            LiquidityPool pool = pools.get(0);
            //logger.info("獲取到的流動性池: id={}, exchange_id={}", pool.getId(), pool.getExchangeId());
            return pool;
        } catch (Exception e) {
            logger.error("獲取流動性池時發生錯誤", e);
            throw e;
        }
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
        
        USDCBalance balance = usdcBalanceRepository.findByAddress(address)
                .orElseThrow(() -> new IllegalStateException("USDC balance not found for address: " + address));
        
        System.out.println("DEBUG: Found balance: " + balance.getBalance());
        return balance.getBalance();
    }

    public LiquidityInfo getLiquidityInfo() {
        //logger.info("開始獲取流動性池信息");
        try {
            LiquidityPool pool = getLiquidityPool();
            // logger.info("獲取到的流動性池信息: id={}, cctReserves={}, usdcReserves={}, totalShares={}", 
            //     pool.getId(), pool.getCctReserves(), pool.getUsdcReserves(), pool.getTotalShares());
            
            LiquidityInfo info = new LiquidityInfo();
            info.setCctReserves(pool.getCctReserves());
            info.setUsdcReserves(pool.getUsdcReserves());
            info.setExchangeRate(pool.calculateExchangeRate());
            info.setTotalShares(pool.getTotalShares());
            
            //logger.info("設置後的流動性信息: cctReserves={}, usdcReserves={}, exchangeRate={}, totalShares={}", 
            //    info.getCctReserves(), info.getUsdcReserves(), info.getExchangeRate(), info.getTotalShares());
            
            return info;
        } catch (Exception e) {
            logger.error("獲取流動性池信息時發生錯誤", e);
            throw e;
        }
    }

    public void removeLiquidity(String userId, BigDecimal amountUsdc, BigDecimal maxSlippagePercentage) {
        LiquidityPool pool = liquidityPoolRepository.findAll().get(0);
        CarbonExchange exchange = carbonExchangeRepository.findById(pool.getId())
            .orElseThrow(() -> new RuntimeException("Exchange not found"));
        LiquidityProvider provider = liquidityProviderRepository.findById(new LiquidityProviderId(exchange.getId(), userId))
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
        LiquidityProvider provider = liquidityProviderRepository.findById(new LiquidityProviderId(exchange.getId(), userId))
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

    @Transactional
    public void swapBaseCurrencyForCarbonCredits(String trader, BigDecimal amountBaseCurrency, BigDecimal maxExchangeRate) {
        try {
            logger.info("開始交換 USDC 換 CCT - trader: {}, amount: {}, maxRate: {}", 
                trader, amountBaseCurrency, maxExchangeRate);
            
            // 獲取流動性池
            LiquidityPool pool = getLiquidityPool();
            if (pool.getCctReserves().compareTo(BigDecimal.ZERO) <= 0 || 
                pool.getUsdcReserves().compareTo(BigDecimal.ZERO) <= 0) {
                throw new RuntimeException("流動性池尚未創建");
            }
            
            // 檢查用戶 USDC 餘額
            USDCBalance usdcBalance = usdcBalanceRepository.findByAddress(trader)
                .orElseThrow(() -> new RuntimeException("找不到 USDC 餘額"));
            if (usdcBalance.getBalance().compareTo(amountBaseCurrency) < 0) {
                throw new RuntimeException("USDC 餘額不足");
            }
            
            // 計算新的匯率
            BigDecimal newUsdcReserves = pool.getUsdcReserves().add(amountBaseCurrency);
            BigDecimal exchangeRate = newUsdcReserves.divide(pool.getCctReserves(), 18, RoundingMode.HALF_UP);
            logger.info("計算出的匯率: {}", exchangeRate);
            
            // 檢查滑點
            if (exchangeRate.compareTo(maxExchangeRate) > 0) {
                throw new RuntimeException("滑點過大");
            }
            
            // 計算可獲得的 CCT 數量
            BigDecimal amountTokens = amountBaseCurrency.multiply(pool.getCctReserves())
                .divide(pool.getUsdcReserves().add(amountBaseCurrency), 18, RoundingMode.DOWN);
            logger.info("可獲得的 CCT 數量: {}", amountTokens);
            
            // 檢查流動性池是否有足夠的 CCT
            if (amountTokens.compareTo(pool.getCctReserves()) > 0) {
                throw new RuntimeException("流動性池 CCT 不足");
            }
            
            // 更新用戶 USDC 餘額
            usdcBalance.setBalance(usdcBalance.getBalance().subtract(amountBaseCurrency));
            usdcBalanceRepository.save(usdcBalance);
            
            // 更新用戶 CCT 餘額
            CarbonCreditToken cct = carbonCreditTokenRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("找不到 CCT token"));
            cct.setBalance(trader, cct.getBalance(trader).add(amountTokens));
            carbonCreditTokenRepository.save(cct);
            
            // 更新流動性池
            pool.setUsdcReserves(newUsdcReserves);
            pool.setCctReserves(pool.getCctReserves().subtract(amountTokens));
            liquidityPoolRepository.save(pool);
            
            logger.info("交換完成 - 新的 USDC 儲備: {}, 新的 CCT 儲備: {}, 用戶 CCT 餘額: {}", 
                pool.getUsdcReserves(), pool.getCctReserves(), cct.getBalance(trader));
        } catch (Exception e) {
            logger.error("交換失敗", e);
            throw e;
        }
    }

    public void setBaseBalance(String address, BigDecimal newBalance) {
        USDCBalance balance = usdcBalanceRepository.findByAddress(address)
                .orElseThrow(() -> new IllegalStateException("USDC balance not found for address: " + address));
        balance.setBalance(newBalance);
        usdcBalanceRepository.save(balance);
    }

    @Transactional
    public void getAsset(String address) {
        try {
            // 獲取用戶當前的 USDC 餘額
            USDCBalance usdcBalance = usdcBalanceRepository.findByAddress(address)
                .orElseThrow(() -> new RuntimeException("找不到 USDC 餘額"));
            
            // 增加 1000 USDC
            usdcBalance.setBalance(usdcBalance.getBalance().add(BigDecimal.valueOf(1000)));
            usdcBalanceRepository.save(usdcBalance);
            
            // 獲取 CCT token
            CarbonCreditToken cct = carbonCreditTokenRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("找不到 CCT token"));
            
            // 增加 1000 CCT
            cct.setBalance(address, cct.getBalance(address).add(BigDecimal.valueOf(1000)));
            carbonCreditTokenRepository.save(cct);
            
            logger.info("成功為用戶 {} 增加資產 - USDC: {}, CCT: {}", 
                address, usdcBalance.getBalance(), cct.getBalance(address));
        } catch (Exception e) {
            logger.error("獲取資產失敗", e);
            throw e;
        }
    }
} 