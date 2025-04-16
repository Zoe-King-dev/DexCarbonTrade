package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.service.CarbonCreditService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/token")
@RequiredArgsConstructor
public class CarbonCreditController {
    private final CarbonCreditService tokenService;

    @PostMapping("/create")
    public ResponseEntity<String> createToken(@RequestParam String adminAddress) {
        tokenService.createToken(adminAddress);
        return ResponseEntity.ok("Token created successfully");
    }

    @PostMapping("/mint")
    public ResponseEntity<String> mint(@RequestParam String adminAddress,
                                     @RequestParam String to,
                                     @RequestParam BigDecimal amount) {
        tokenService.mint(adminAddress, to, amount);
        return ResponseEntity.ok("Tokens minted successfully");
    }

    @PostMapping("/disable-minting")
    public ResponseEntity<String> disableMinting(@RequestParam String adminAddress) {
        tokenService.disableMinting(adminAddress);
        return ResponseEntity.ok("Minting disabled successfully");
    }

    @PostMapping("/transfer")
    public ResponseEntity<String> transfer(@RequestParam String from,
                                         @RequestParam String to,
                                         @RequestParam BigDecimal amount) {
        tokenService.transfer(from, to, amount);
        return ResponseEntity.ok("Transfer successful");
    }

    @PostMapping("/approve")
    public ResponseEntity<String> approve(@RequestParam String owner,
                                        @RequestParam String spender,
                                        @RequestParam BigDecimal amount) {
        tokenService.approve(owner, spender, amount);
        return ResponseEntity.ok("Approval successful");
    }

    @PostMapping("/transfer-from")
    public ResponseEntity<String> transferFrom(@RequestParam String owner,
                                             @RequestParam String spender,
                                             @RequestParam String to,
                                             @RequestParam BigDecimal amount) {
        tokenService.transferFrom(owner, spender, to, amount);
        return ResponseEntity.ok("Transfer successful");
    }

    @GetMapping("/balance")
    public ResponseEntity<BigDecimal> balanceOf(@RequestParam String address) {
        return ResponseEntity.ok(tokenService.balanceOf(address));
    }

    @GetMapping("/allowance")
    public ResponseEntity<BigDecimal> allowance(@RequestParam String owner,
                                              @RequestParam String spender) {
        return ResponseEntity.ok(tokenService.allowance(owner, spender));
    }
} 