package org.example.controller;

import lombok.RequiredArgsConstructor;
import org.example.model.User;
import org.example.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class UserController {
    private final UserService userService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody Map<String, String> request) {
        try {
            // 驗證必要參數
            String username = request.get("username");
            String password = request.get("password");
            String userTypeStr = request.get("userType");
            String address = request.get("address");

            if (username == null || username.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "用戶名不能為空"));
            }
            if (password == null || password.length() < 6) {
                return ResponseEntity.badRequest().body(Map.of("error", "密碼長度至少為6位"));
            }
            if (address == null || address.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "錢包地址不能為空"));
            }
            if (userTypeStr == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "用戶類型不能為空"));
            }

            // 驗證用戶類型
            User.UserType userType;
            try {
                userType = User.UserType.valueOf(userTypeStr);
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().body(Map.of("error", "無效的用戶類型"));
            }

            // 註冊用戶
            User user = userService.register(username.trim(), password, userType, address.trim());
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Registration successful");
            response.put("userId", user.getId());
            response.put("userType", user.getUserType());
            response.put("username", user.getUsername());
            
            return ResponseEntity.ok(response);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "註冊失敗：" + e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request) {
        try {
            String username = request.get("username");
            String password = request.get("password");

            if (username == null || username.trim().isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "用戶名不能為空"));
            }
            if (password == null || password.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "密碼不能為空"));
            }

            return userService.login(username.trim(), password)
                    .map(user -> {
                        Map<String, Object> response = new HashMap<>();
                        response.put("message", "Login successful");
                        response.put("userId", user.getId());
                        response.put("userType", user.getUserType());
                        response.put("username", user.getUsername());
                        response.put("address", user.getAddress());
                        return ResponseEntity.ok(response);
                    })
                    .orElse(ResponseEntity.badRequest().body(Map.of("error", "用戶名或密碼錯誤")));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "登錄失敗：" + e.getMessage()));
        }
    }
} 