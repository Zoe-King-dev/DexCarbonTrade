package org.example.service;

import lombok.RequiredArgsConstructor;
import org.example.model.User;
import org.example.repository.UserRepository;
import org.example.model.CarbonCreditToken;
import org.example.repository.CarbonCreditTokenRepository;
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
            initializeUserBalances(user);
        }

        return user;
    }

    @Transactional
    protected void initializeUserBalances(User user) {
        try {
            // 獲取 CCT token
            CarbonCreditToken token = tokenRepository.findAll().stream()
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Token not found"));

            // 檢查是否有管理員權限
            String adminAddress = token.getAdminAddress();
            
            // 鑄造 100 CCT 給新用戶
            carbonCreditService.mint(adminAddress, user.getAddress(), BigDecimal.valueOf(100));
            
            // 添加 10000 USDC 基礎貨幣（這個在實際環境中需要通過其他方式處理）
            // 在這裡我們假設已經處理了 USDC 的轉賬
            // TODO: 實現 USDC 轉賬邏輯
        } catch (Exception e) {
            throw new IllegalStateException("Failed to initialize user balances: " + e.getMessage());
        }
    }

    public Optional<User> login(String username, String password) {
        return userRepository.findByUsername(username)
                .filter(user -> passwordEncoder.matches(password, user.getPassword()));
    }
} 