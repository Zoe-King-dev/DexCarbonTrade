package org.example.repository;

import org.example.model.LiquidityProvider;
import org.example.model.LiquidityProviderId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LiquidityProviderRepository extends JpaRepository<LiquidityProvider, LiquidityProviderId> {
} 