package org.example.model;

import lombok.Data;
import java.io.Serializable;

@Data
public class LiquidityProviderId implements Serializable {
    private Long exchangeId;
    private String providerAddress;
    
    public LiquidityProviderId() {
    }
    
    public LiquidityProviderId(Long exchangeId, String providerAddress) {
        this.exchangeId = exchangeId;
        this.providerAddress = providerAddress;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        
        LiquidityProviderId that = (LiquidityProviderId) o;
        
        if (exchangeId != null ? !exchangeId.equals(that.exchangeId) : that.exchangeId != null) return false;
        return providerAddress != null ? providerAddress.equals(that.providerAddress) : that.providerAddress == null;
    }
    
    @Override
    public int hashCode() {
        int result = exchangeId != null ? exchangeId.hashCode() : 0;
        result = 31 * result + (providerAddress != null ? providerAddress.hashCode() : 0);
        return result;
    }
} 