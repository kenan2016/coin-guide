package com.exchange.simple.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;

/**
 * 订单实体类
 * 对应数据库表: order_ent (避免使用 SQL 关键字 order)
 * 作用: 记录用户的委托单详情
 */
@Data
@TableName("order_ent")
public class OrderEnt {
    
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 下单用户ID
     */
    private Long userId;

    /**
     * 交易对，例如 "BTC/USDT"
     */
    private String symbol;

    /**
     * 交易方向
     * BUY  = 买入
     * SELL = 卖出
     */
    private String direction;

    /**
     * 委托价格 (限价单价格)
     */
    private BigDecimal price;

    /**
     * 委托总数量
     * 解释: 用户原本想要买/卖的总个数
     */
    private BigDecimal amount;

    /**
     * 已成交数量
     * 解释: 随着撮合进行，这个数字会不断增加。
     * 当 tradedAmount == amount 时，订单完成。
     */
    private BigDecimal tradedAmount;

    /**
     * 订单状态
     * PENDING   = 进行中 (排队或部分成交)
     * COMPLETED = 已完成 (全部成交)
     * CANCELED  = 已撤销
     */
    private String status;
}