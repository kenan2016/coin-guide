package com.exchange.simple.consumer;

import com.alibaba.fastjson.JSON;
import com.exchange.simple.event.TradeEvent;
import com.exchange.simple.service.TradeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;


/**
 * 消费者 A - 资金结算 (Settlement)
 * 这个组件负责监听 Kafka，慢慢写数据库。即使数据库慢，也不会卡住撮合引擎。
 */
@Component
public class TradeSettlementConsumer {

    @Autowired
    private TradeService tradeService; // 复用之前的 DB 逻辑

    @KafkaListener(topics = "trade-topic")
    public void onTrade(String message) {
        TradeEvent event = JSON.parseObject(message, TradeEvent.class);
        
        // 调用之前的数据库逻辑进行结算
        // 注意：这里需要把之前的 handleMatch 里的 DB 逻辑剥离出来单独封装成方法，比如 executeSettlement
        System.out.println(">>> [DB消费者] 收到成交，正在落库: " + event.getPrice());
        
         tradeService.executeSettlement(event);
    }
}