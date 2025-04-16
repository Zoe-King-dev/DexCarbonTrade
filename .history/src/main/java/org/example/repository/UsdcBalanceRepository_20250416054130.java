package org.example.repository;

import org.example.model.USDCBalance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UsdcBalanceRepository extends JpaRepository<USDCBalance, String> {
    USDCBalance findByAddress(String address);
} 