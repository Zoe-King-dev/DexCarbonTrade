package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.model.CarbonCreditToken;
import org.example.repository.CarbonCreditTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class CarbonCreditService {
    private final CarbonCreditTokenRepository tokenRepository;

    @Transactional
    public CarbonCreditToken createToken(String adminAddress) {
        CarbonCreditToken token = new CarbonCreditToken(adminAddress);
        return tokenRepository.save(token);
    }

    @Transactional
    public void mint(String adminAddress, String to, BigDecimal amount) {
        CarbonCreditToken token = tokenRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Token not found"));
        
        if (!token.getAdminAddress().equals(adminAddress)) {
            throw new IllegalStateException("Only admin can mint tokens");
        }
        
        token.mint(to, amount);
        tokenRepository.save(token);
    }

    @Transactional
    public void disableMinting(String adminAddress) {
        CarbonCreditToken token = tokenRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Token not found"));
        
        if (!token.getAdminAddress().equals(adminAddress)) {
            throw new IllegalStateException("Only admin can disable minting");
        }
        
        token.disableMinting();
        tokenRepository.save(token);
    }

    @Transactional
    public void transfer(String from, String to, BigDecimal amount) {
        CarbonCreditToken token = tokenRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Token not found"));
        
        token.transfer(from, to, amount);
        tokenRepository.save(token);
    }

    @Transactional
    public void approve(String owner, String spender, BigDecimal amount) {
        CarbonCreditToken token = tokenRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Token not found"));
        
        token.approve(owner, spender, amount);
        tokenRepository.save(token);
    }

    @Transactional
    public void transferFrom(String owner, String spender, String to, BigDecimal amount) {
        CarbonCreditToken token = tokenRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Token not found"));
        
        token.transferFrom(owner, spender, to, amount);
        tokenRepository.save(token);
    }

    public BigDecimal balanceOf(String address) {
        CarbonCreditToken token = tokenRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Token not found"));
        
        return token.balanceOf(address);
    }

    public BigDecimal allowance(String owner, String spender) {
        CarbonCreditToken token = tokenRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Token not found"));
        
        return token.allowance(owner, spender);
    }
} 