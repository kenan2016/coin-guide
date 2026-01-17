package com.exchange.simple.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

// ContractWallet.java
@Data
@TableName("contract_wallet")
public class ContractWallet {
    @TableId(type = IdType.AUTO) private Long id;
    private Long userId;
    private String coin;
    private BigDecimal balance;
    private BigDecimal frozen;
}

