package com.exchange.simple.vo;
import lombok.Data;
import java.math.BigDecimal;
import java.util.List;

@Data
public class DepthVO {
    private String symbol;
    private List<PriceVol> bids; // 买单深度 [买1, 买2, ...]
    private List<PriceVol> asks; // 卖单深度 [卖1, 卖2, ...]

    @Data
    public static class PriceVol {
        private BigDecimal price;
        private BigDecimal amount;
        
        public PriceVol(BigDecimal p, BigDecimal a) {
            this.price = p; this.amount = a;
        }
    }
}