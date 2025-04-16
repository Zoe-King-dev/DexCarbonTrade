package org.example.repository;

import org.example.model.CarbonExchange;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CarbonExchangeRepository extends JpaRepository<CarbonExchange, Long> {
} 