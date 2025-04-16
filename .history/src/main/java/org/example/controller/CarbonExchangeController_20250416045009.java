package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.model.LiquidityInfo;
import org.example.service.CarbonExchangeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/exchange")
@RequiredArgsConstructor
public class CarbonExchangeController {
    private final CarbonExchangeService exchangeService;

    @GetMapping("/liquidity")
    public ResponseEntity<LiquidityInfo> getLiquidity(@RequestParam String address) {
        return ResponseEntity.ok(exchangeService.getLiquidity(address));
    }

    @GetMapping("/exchange-rate")
    public ResponseEntity<BigDecimal> getExchangeRate() {
        return ResponseEntity.ok(exchangeService.calculateExchangeRate());
    }

    @GetMapping("/base-balance")
    public ResponseEntity<BigDecimal> getBaseBalance(@RequestParam String address) {
        return ResponseEntity.ok(exchangeService.getBaseBalance(address));
    }

    @GetMapping("/pool-status")
    public ResponseEntity<Map<String, Object>> getPoolStatus() {
        Map<String, Object> response = new HashMap<>();
        try {
            LiquidityInfo liquidity = exchangeService.getLiquidity("admin");
            response.put("isEmpty", liquidity.getTotalCarbonReserves().equals(BigDecimal.ZERO) && 
                                  liquidity.getTotalBaseReserves().equals(BigDecimal.ZERO));
            response.put("totalCarbonReserves", liquidity.getTotalCarbonReserves());
            response.put("totalBaseReserves", liquidity.getTotalBaseReserves());
            response.put("exchangeRate", exchangeService.calculateExchangeRate());
        } catch (Exception e) {
            response.put("isEmpty", true);
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/add-liquidity")
    public ResponseEntity<String> addLiquidity(@RequestBody Map<String, String> request) {
        try {
            String userId = request.get("userId");
            BigDecimal amountUsdc = new BigDecimal(request.get("amountUsdc"));
            BigDecimal maxSlippagePercentage = new BigDecimal(request.get("maxSlippagePercentage"));
            
            exchangeService.addLiquidity(userId, amountUsdc, maxSlippagePercentage);
            return ResponseEntity.ok("Liquidity added successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/remove-liquidity")
    public ResponseEntity<String> removeLiquidity(@RequestBody Map<String, String> request) {
        try {
            String userId = request.get("userId");
            BigDecimal amountUsdc = new BigDecimal(request.get("amountUsdc"));
            BigDecimal maxSlippagePercentage = new BigDecimal(request.get("maxSlippagePercentage"));
            
            exchangeService.removeLiquidity(userId, amountUsdc, maxSlippagePercentage);
            return ResponseEntity.ok("Liquidity removed successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/remove-all-liquidity")
    public ResponseEntity<String> removeAllLiquidity(@RequestBody Map<String, String> request) {
        try {
            String userId = request.get("userId");
            BigDecimal maxSlippagePercentage = new BigDecimal(request.get("maxSlippagePercentage"));
            
            exchangeService.removeAllLiquidity(userId, maxSlippagePercentage);
            return ResponseEntity.ok("All liquidity removed successfully");
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/swap-carbon-for-base")
    public ResponseEntity<String> swapCarbonCreditsForBaseCurrency(@RequestParam String trader,
                                                                 @RequestParam BigDecimal amountCarbonCredits,
                                                                 @RequestParam BigDecimal maxExchangeRate) {
        exchangeService.swapCarbonCreditsForBaseCurrency(trader, amountCarbonCredits, maxExchangeRate);
        return ResponseEntity.ok("Swap successful");
    }

    @PostMapping("/swap-base-for-carbon")
    public ResponseEntity<String> swapBaseCurrencyForCarbonCredits(@RequestParam String trader,
                                                                 @RequestParam BigDecimal amountBaseCurrency,
                                                                 @RequestParam BigDecimal maxExchangeRate) {
        exchangeService.swapBaseCurrencyForCarbonCredits(trader, amountBaseCurrency, maxExchangeRate);
        return ResponseEntity.ok("Swap successful");
    }
} 