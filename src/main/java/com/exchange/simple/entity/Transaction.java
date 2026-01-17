package com.exchange.simple.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 资产流水实体类
 * 对应数据库表: transaction
 * 作用: 记录每一笔资金的进出，是财务对账的核心依据。
 */
@Data
@TableName("transaction")
public class Transaction {
    
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 币种 (如 USDT, BTC) */
    private String coin;

    /** 数量 (充值数量 或 提现数量) */
    private BigDecimal amount;

    /** * 流水类型
     * DEPOSIT  = 充币 (外部转入)
     * WITHDRAW = 提币 (转出到外部)
     */
    private String type;

    /**
     * 当前状态
     * PENDING = 处理中 (提现一般先进入此状态，等待人工或机器审核)
     * SUCCESS = 成功 (资金已到账或已发出)
     * FAILED  = 失败 (审核拒绝或链上确认失败)
     */
    private String status;

    /** 提币目标地址 (充值时通常为空，或者记录来源地址) */
    private String address;

    /** * 链上交易哈希 (Transaction Hash)
     * 1. 充值时: 这是去重(幂等)的关键，防止同一笔链上转账被重复入账。
     * 2. 提现时: 只有状态变为 SUCCESS 后，这里才会填入发出的交易哈希。
     */
    private String txHash;
    
    /** 创建时间 (自动填充) */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 (自动填充) */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}