package org.example.repository;

import org.example.model.UsdcBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UsdcBalanceRepository extends JpaRepository<UsdcBalance, String> {
    Optional<UsdcBalance> findByAddress(String address);
} 