package org.example.repository;

import org.example.model.CarbonExchange;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface CarbonExchangeRepository extends JpaRepository<CarbonExchange, Long> {
    @EntityGraph(value = "exchange.withBalances", type = EntityGraph.EntityGraphType.LOAD)
    @Query("SELECT e FROM CarbonExchange e LEFT JOIN FETCH e.baseBalances WHERE e.id = :id")
    Optional<CarbonExchange> findById(@Param("id") Long id);
} 