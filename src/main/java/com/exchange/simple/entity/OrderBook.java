package com.exchange.simple.entity;

import com.exchange.simple.entity.OrderEnt;
import lombok.Data;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.function.Consumer;

/**
 * 核心订单簿 (OrderBook)
 * 作用: 每个交易对（如 BTC/USDT）都在内存中有一个 OrderBook 对象。
 * 它负责维护买卖队列，并进行即时撮合。
 */
public class OrderBook {
    private String symbol;

    /**
     * 买单队列 (Buy Queue)
     * 排序规则: 价格从高到低 (Price Desc)。
     * 解释: 谁出价最高，谁就排在最前面，最先成交。
     */
    private PriorityQueue<OrderEnt> buyQueue = new PriorityQueue<>((o1, o2) -> 
        o2.getPrice().compareTo(o1.getPrice()));

    /**
     * 卖单队列 (Sell Queue)
     * 排序规则: 价格从低到高 (Price Asc)。
     * 解释: 谁卖得最便宜，谁就排在最前面，最先成交。
     */
    private PriorityQueue<OrderEnt> sellQueue = new PriorityQueue<>(
        Comparator.comparing(OrderEnt::getPrice));

    public OrderBook(String symbol) {
        this.symbol = symbol;
    }

    /**
     * 处理新订单 (撮合入口)
     * @param takerOrder 新进来的订单 (Taker)
     * @param matchCallback 回调函数，当撮合成功时通知 Service 层去写数据库
     */
    public void processOrder(OrderEnt takerOrder, Consumer<TradeMatch> matchCallback) {
        if ("BUY".equals(takerOrder.getDirection())) {
            // 如果是买单，就去卖单队列里找匹配
            match(takerOrder, sellQueue, buyQueue, matchCallback);
        } else {
            // 如果是卖单，就去买单队列里找匹配
            match(takerOrder, buyQueue, sellQueue, matchCallback);
        }
    }

    /**
     * 通用撮合逻辑
     * @param taker 主动单 (新来的)
     * @param makerQueue 对手单队列 (挂在上面的)
     * @param sameSideQueue 同向队列 (如果没吃完，要进这里排队)
     * @param callback 成功回调
     */
    private void match(OrderEnt taker, PriorityQueue<OrderEnt> makerQueue, 
                       PriorityQueue<OrderEnt> sameSideQueue, Consumer<TradeMatch> callback) {
        
        // 循环条件: 
        // 1. 我的单子还有剩余数量 (taker.amount > 0)
        // 2. 对手队列里还有单子 (!makerQueue.isEmpty())
        while (taker.getAmount().compareTo(BigDecimal.ZERO) > 0 && !makerQueue.isEmpty()) {
            OrderEnt maker = makerQueue.peek(); // 看一眼对手队列的第一单

            // 价格校验:
            // 如果我是买单(BUY): 我的出价必须 >= 对手卖价
            // 如果我是卖单(SELL): 我的出价必须 <= 对手买价
            boolean isMatched;
            if ("BUY".equals(taker.getDirection())) {
                isMatched = taker.getPrice().compareTo(maker.getPrice()) >= 0;
            } else {
                isMatched = taker.getPrice().compareTo(maker.getPrice()) <= 0;
            }

            if (!isMatched) {
                break; // 价格谈不拢，停止撮合，退出循环
            }

            // --- ⚡️ 撮合成功 ---
            
            // 成交价: 永远以"挂单方(Maker)"的价格为准
            BigDecimal matchPrice = maker.getPrice(); 
            // 成交量: 取两者中较小的那个数量
            BigDecimal matchAmount = taker.getAmount().min(maker.getAmount()); 

            // 1. 更新内存中的订单数量 (扣除成交部分)
            taker.setAmount(taker.getAmount().subtract(matchAmount));
            maker.setAmount(maker.getAmount().subtract(matchAmount));

            // 2. 触发回调: 告诉外部 Service 层，这俩ID成交了多少钱、多少币
            callback.accept(new TradeMatch(taker.getId(), maker.getId(), matchPrice, matchAmount));

            // 3. 检查 Maker: 如果对手单被吃光了 (amount == 0)，从队列中移除
            if (maker.getAmount().compareTo(BigDecimal.ZERO) == 0) {
                makerQueue.poll(); // 移除队头
            }
        }

        // 4. 循环结束后，如果我的单子还没吃饱 (amount > 0)，放入同向队列挂单
        if (taker.getAmount().compareTo(BigDecimal.ZERO) > 0) {
            sameSideQueue.add(taker);
        }
    }

    /**
     * 内部类: 用于封装撮合结果
     */
    @Data
    public static class TradeMatch {
        private Long takerId;  // 主动单ID
        private Long makerId;  // 被动单ID
        private BigDecimal price;  // 成交价
        private BigDecimal amount; // 成交量

        public TradeMatch(Long t, Long m, BigDecimal p, BigDecimal a) {
            this.takerId = t; this.makerId = m; this.price = p; this.amount = a;
        }
    }
}