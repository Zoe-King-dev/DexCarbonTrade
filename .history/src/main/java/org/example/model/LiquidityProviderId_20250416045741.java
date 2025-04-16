package org.example.model;

import lombok.Data;
import java.io.Serializable;

@Data
public class LiquidityProviderId implements Serializable {
    private Long poolId;
    private String providerAddress;
    
    public LiquidityProviderId() {
    }
    
    public LiquidityProviderId(Long poolId, String providerAddress) {
        this.poolId = poolId;
        this.providerAddress = providerAddress;
    }
} 