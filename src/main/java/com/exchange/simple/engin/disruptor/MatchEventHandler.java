package com.exchange.simple.engin.disruptor;

import com.exchange.simple.entity.OrderBook;
import com.exchange.simple.entity.OrderEnt;
import com.exchange.simple.service.TradeService;
import com.lmax.disruptor.EventHandler;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class MatchEventHandler implements EventHandler<OrderEvent> {

    // 所有的 OrderBook 都维护在这里，外部无法直接访问，保证线程绝对安全
    private final Map<String, OrderBook> orderBookMap = new HashMap<>();
    
    private final TradeService tradeService;

    public MatchEventHandler(TradeService tradeService) {
        this.tradeService = tradeService;
    }

    @Override
    public void onEvent(OrderEvent event, long sequence, boolean endOfBatch) {
        OrderEnt order = event.getOrder();
        if (order == null) return;

        // 1. 找到对应的 OrderBook (比如 BTC/USDT)
        // computeIfAbsent 在这里是安全的，因为 onEvent 是单线程串行执行的
        OrderBook engine = orderBookMap.computeIfAbsent(order.getSymbol(), OrderBook::new);

        // 2. 执行撮合 (直接调用，无需 synchronized)
        // 注意：我们需要把 TradeService 里的 handleMatch 传进去
        engine.processOrder(order, tradeService::handleMatch);
        
        // 3. (可选) 撮合完后，可以在这里触发深度推送，或者攒够一批再推
        // tradeService.pushDepthData(order.getSymbol());
    }
}