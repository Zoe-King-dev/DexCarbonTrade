package org.example.repository;

import org.example.model.LiquidityPool;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface LiquidityPoolRepository extends JpaRepository<LiquidityPool, Long> {
    Optional<LiquidityPool> findByExchangeId(Long exchangeId);
} 