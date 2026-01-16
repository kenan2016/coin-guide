package com.exchange.simple.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;

/**
 * 钱包实体类
 * 对应数据库表: wallet
 * 作用: 管理用户的资金状态（可用 vs 冻结）
 */
@Data
@TableName("wallet")
public class Wallet {
    
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户ID
     */
    private Long userId;

    /**
     * 币种名称，例如: "BTC", "USDT"
     */
    private String coin;

    /**
     * 可用余额
     * 解释: 用户可以随意支配、提现或用于下单的钱。
     */
    private BigDecimal balance;

    /**
     * 冻结余额
     * 解释: 用户挂单后被锁定的钱。
     * 例如: 用户有 100 USDT，挂单买入用了 20 USDT，
     * 此时 balance=80, frozen=20。只有当撤单或成交后，这部分钱才会变动。
     */
    private BigDecimal frozen;
}