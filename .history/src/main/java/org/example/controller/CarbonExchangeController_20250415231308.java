package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.service.CarbonExchangeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/exchange")
@RequiredArgsConstructor
public class CarbonExchangeController {
    private final CarbonExchangeService exchangeService;

    @PostMapping("/create")
    public ResponseEntity<String> createExchange(@RequestParam String adminAddress) {
        exchangeService.createExchange(adminAddress);
        return ResponseEntity.ok("Exchange created successfully");
    }

    @PostMapping("/create-pool")
    public ResponseEntity<String> createPool(@RequestParam String adminAddress,
                                           @RequestParam BigDecimal amountCarbonCredits,
                                           @RequestParam BigDecimal amountBaseCurrency) {
        exchangeService.createPool(adminAddress, amountCarbonCredits, amountBaseCurrency);
        return ResponseEntity.ok("Pool created successfully");
    }

    @PostMapping("/add-liquidity")
    public ResponseEntity<String> addLiquidity(@RequestParam String provider,
                                             @RequestParam BigDecimal amountBaseCurrency,
                                             @RequestParam BigDecimal minExchangeRate,
                                             @RequestParam BigDecimal maxExchangeRate) {
        exchangeService.addLiquidity(provider, amountBaseCurrency, minExchangeRate, maxExchangeRate);
        return ResponseEntity.ok("Liquidity added successfully");
    }

    @PostMapping("/remove-liquidity")
    public ResponseEntity<String> removeLiquidity(@RequestParam String provider,
                                                @RequestParam BigDecimal amountBaseCurrency,
                                                @RequestParam BigDecimal minExchangeRate,
                                                @RequestParam BigDecimal maxExchangeRate) {
        exchangeService.removeLiquidity(provider, amountBaseCurrency, minExchangeRate, maxExchangeRate);
        return ResponseEntity.ok("Liquidity removed successfully");
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
} 