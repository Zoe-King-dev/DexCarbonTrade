package org.example.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@NoArgsConstructor
public class LiquidityInfo {
    private BigDecimal userLiquidity = BigDecimal.ZERO;
    private BigDecimal totalCarbonReserves = BigDecimal.ZERO;
    private BigDecimal totalBaseReserves = BigDecimal.ZERO;
    private BigDecimal userSharePercentage = BigDecimal.ZERO;
    private BigDecimal currentExchangeRate = BigDecimal.ZERO;
} 