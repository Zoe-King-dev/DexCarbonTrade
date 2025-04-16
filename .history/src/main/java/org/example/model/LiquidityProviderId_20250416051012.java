package org.example.model;

import lombok.Data;
import java.io.Serializable;

@Data
public class LiquidityProviderId implements Serializable {
    private Long exchange;
    private String providerAddress;
    private Long poolId;
    
    public LiquidityProviderId() {
    }
    
    public LiquidityProviderId(Long exchange, String providerAddress, Long poolId) {
        this.exchange = exchange;
        this.providerAddress = providerAddress;
        this.poolId = poolId;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        LiquidityProviderId that = (LiquidityProviderId) o;
        
        if (exchange != null ? !exchange.equals(that.exchange) : that.exchange != null) return false;
        if (providerAddress != null ? !providerAddress.equals(that.providerAddress) : that.providerAddress != null) return false;
        return poolId != null ? poolId.equals(that.poolId) : that.poolId == null;
    }
    
    @Override
    public int hashCode() {
        int result = exchange != null ? exchange.hashCode() : 0;
        result = 31 * result + (providerAddress != null ? providerAddress.hashCode() : 0);
        result = 31 * result + (poolId != null ? poolId.hashCode() : 0);
        return result;
    }
} 