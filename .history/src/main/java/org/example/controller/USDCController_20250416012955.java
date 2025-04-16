package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.service.USDCService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/usdc")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class USDCController {
    private final USDCService usdcService;

    @GetMapping("/balance")
    public ResponseEntity<BigDecimal> getBalance(@RequestParam String address) {
        try {
            BigDecimal balance = usdcService.getBalance(address);
            return ResponseEntity.ok(balance);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/transfer")
    public ResponseEntity<String> transfer(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam BigDecimal amount) {
        try {
            usdcService.transfer(from, to, amount);
            return ResponseEntity.ok("Transfer successful");
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
} 