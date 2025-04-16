package org.example.repository;

import org.example.model.CarbonExchange;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface CarbonExchangeRepository extends JpaRepository<CarbonExchange, Long> {
    @EntityGraph(value = "exchange.withBalances", type = EntityGraph.EntityGraphType.LOAD)
    Optional<CarbonExchange> findById(Long id);
} 