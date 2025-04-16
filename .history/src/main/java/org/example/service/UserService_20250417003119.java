package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.model.User;
import org.example.repository.UserRepository;
import org.example.model.CarbonCreditToken;
import org.example.repository.CarbonCreditTokenRepository;
import org.example.model.LiquidityPool;
import org.example.repository.LiquidityPoolRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CarbonCreditTokenRepository tokenRepository;
    private final CarbonCreditService carbonCreditService;
    private final USDCService usdcService;
    private final LiquidityPoolRepository liquidityPoolRepository;

    @Transactional
    public User register(String username, String password, User.UserType userType, String address) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new IllegalStateException("Username already exists");
        }
        if (userRepository.findByAddress(address).isPresent()) {
            throw new IllegalStateException("Address already exists");
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setUserType(userType);
        user.setAddress(address);

        user = userRepository.save(user);

        // 如果是公司用戶，初始化餘額
        if (userType == User.UserType.COMPANY) {
            try {
                initializeUserBalances(user);
            } catch (Exception e) {
                // 如果初始化餘額失敗，刪除用戶
                userRepository.delete(user);
                throw new IllegalStateException("Failed to initialize user balances: " + e.getMessage());
            }
        }

        return user;
    }

    @Transactional
    protected void initializeUserBalances(User user) {
        // 獲取 CCT token
        CarbonCreditToken token = tokenRepository.findAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                    "System is not properly initialized: CCT token not found. Please contact administrator."
                ));

        // 檢查是否有管理員權限
        String adminAddress = token.getAdminAddress();
        if (adminAddress == null || adminAddress.trim().isEmpty()) {
            throw new IllegalStateException(
                "System is not properly configured: Admin address is not set. Please contact administrator."
            );
        }

        try {
            // 鑄造 100 CCT 給新用戶
            carbonCreditService.mint(adminAddress, user.getAddress(), BigDecimal.valueOf(1000));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to mint CCT tokens: " + e.getMessage());
        }

        try {
            // 添加 10000 USDC
            LiquidityPool pool = liquidityPoolRepository.findAll().get(0);
            usdcService.initializeBalance(user.getAddress(), BigDecimal.valueOf(10000), pool.getExchangeId());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize USDC balance: " + e.getMessage());
        }
    }

    public Optional<User> login(String username, String password) {
        return userRepository.findByUsername(username)
                .filter(user -> passwordEncoder.matches(password, user.getPassword()));
    }
} 