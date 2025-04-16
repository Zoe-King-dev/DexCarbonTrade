package org.example.model;

import lombok.Data;
import java.io.Serializable;

@Data
public class LiquidityProviderId implements Serializable {
    private Long exchange;
    private String providerAddress;
    
    public LiquidityProviderId() {
    }
    
    public LiquidityProviderId(Long exchange, String providerAddress) {
        this.exchange = exchange;
        this.providerAddress = providerAddress;
    }
} 