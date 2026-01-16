package com.exchange.simple.event;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class TradeEvent {
    private Long takerId;
    private Long makerId;
    private String symbol;
    private BigDecimal price;
    private BigDecimal amount;
    private Long timestamp;
}