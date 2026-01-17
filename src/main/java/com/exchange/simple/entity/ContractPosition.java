package com.exchange.simple.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

// ContractPosition.java
@Data 
@TableName("contract_position")
public class ContractPosition {
    @TableId(type = IdType.AUTO) private Long id;
    private Long userId;
    private String symbol;
    private String direction; // LONG, SHORT
    private BigDecimal leverage;
    private BigDecimal size;       // 持仓量
    private BigDecimal entryPrice; // 开仓均价
    private BigDecimal margin;     // 保证金
}
