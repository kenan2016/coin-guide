package com.exchange.simple.engin.disruptor;

import com.exchange.simple.entity.OrderEnt;
import lombok.Data;

@Data
public class OrderEvent {
    private OrderEnt order; // 待撮合的订单
    // 如果有其他操作类型（如撤单），可以加一个 type 字段
    // private int type; 
}