package org.example.repository;

import org.example.model.LiquidityPool;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LiquidityPoolRepository extends JpaRepository<LiquidityPool, Long> {
    LiquidityPool findByExchangeId(Long exchangeId);
} 