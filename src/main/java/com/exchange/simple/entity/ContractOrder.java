package com.exchange.simple.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

// ContractOrder.java
@Data
@TableName("contract_order")
public class ContractOrder {
    @TableId(type = IdType.AUTO) private Long id;
    private Long userId;
    private String symbol;
    private String direction; // BUY, SELL
    private BigDecimal price;
    private BigDecimal amount;
    private BigDecimal leverage;
    private String status;
}
