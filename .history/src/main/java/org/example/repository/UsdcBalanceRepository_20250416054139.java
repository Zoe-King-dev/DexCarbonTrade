package org.example.repository;

import org.example.model.USDCBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UsdcBalanceRepository extends JpaRepository<USDCBalance, String> {
    Optional<USDCBalance> findByAddress(String address);
} 