package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.model.CarbonCreditToken;
import org.example.repository.CarbonCreditTokenRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InitializationService {
    private final CarbonCreditTokenRepository tokenRepository;
    private static final String ADMIN_ADDRESS = "0x1234567890123456789012345678901234567890"; // 系統管理員地址

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void initialize() {
        // 檢查是否已經存在 CCT token
        if (tokenRepository.findAll().isEmpty()) {
            // 創建新的 CCT token
            CarbonCreditToken token = new CarbonCreditToken(
                "Carbon Credit Token",
                "CCT",
                ADMIN_ADDRESS
            );
            tokenRepository.save(token);
        }
    }
} 