package com.exchange.simple.service;

import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.exchange.simple.entity.*;
import com.exchange.simple.event.TradeEvent;
import com.exchange.simple.mapper.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TradeService {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private WalletMapper walletMapper;
    @Autowired
    private OrderMapper orderMapper;

    // 内存中的引擎容器: Key=交易对(如"BTC/USDT"), Value=订单簿对象
    private final Map<String, OrderBook> orderBookMap = new ConcurrentHashMap<>();

    public TradeService() {
        // 项目启动时，初始化 BTC/USDT 的撮合引擎
        // 实际项目中这里应该从数据库读取所有支持的交易对进行初始化
        orderBookMap.put("BTC/USDT", new OrderBook("BTC/USDT"));
    }

    /**
     * 下单入口方法
     * 步骤: 验资 -> 冻结资金 -> 落库订单 -> 发送撮合
     */
    @Transactional(rollbackFor = Exception.class) // 开启事务，任何一步报错都回滚
    public String placeOrder(Long userId, String symbol, String direction, BigDecimal price, BigDecimal amount) {

        // --- 1. 计算冻结金额 ---
        // 规则: 
        // 买 BTC -> 需要花 USDT -> 冻结 USDT = 价格 * 数量
        // 卖 BTC -> 需要出 BTC  -> 冻结 BTC  = 数量
        String coinToFreeze = "BUY".equals(direction) ? symbol.split("/")[1] : symbol.split("/")[0];
        BigDecimal freezeAmount = "BUY".equals(direction) ? price.multiply(amount) : amount;

        // --- 2. 检查并冻结资金 (DB操作) ---
        Wallet wallet = walletMapper.selectOne(new QueryWrapper<Wallet>().eq("user_id", userId).eq("coin", coinToFreeze));

        // 余额不足校验
        if (wallet == null || wallet.getBalance().compareTo(freezeAmount) < 0) {
            return "下单失败：余额不足";
        }

        // 扣除可用，增加冻结
        wallet.setBalance(wallet.getBalance().subtract(freezeAmount));
        wallet.setFrozen(wallet.getFrozen().add(freezeAmount));
        walletMapper.updateById(wallet);

        // --- 3. 保存订单到数据库 (DB操作) ---
        OrderEnt order = new OrderEnt();
        order.setUserId(userId);
        order.setSymbol(symbol);
        order.setDirection(direction);
        order.setPrice(price);
        order.setAmount(amount); // 注意：这里存的是"当前未成交数量"，初始值=总数量
        order.setTradedAmount(BigDecimal.ZERO);
        order.setStatus("PENDING");
        orderMapper.insert(order);

        // --- 4. 发送给内存引擎撮合 (Memory操作) ---
        OrderBook engine = orderBookMap.get(symbol);
        if (engine == null) return "不支持该交易对";

        // 加锁: 保证同一个交易对的撮合是串行的，防止并发导致数据错乱
        synchronized (engine) {
            // processOrder 接受一个回调函数 this::handleMatch
            // 意思是：如果撮合成功了，请执行下面的 handleMatch 方法
            engine.processOrder(order, this::handleMatch);
        }

        return "下单成功，订单ID: " + order.getId();
    }


    /**
     * 2. 撮合成功回调 (Producer)
     * 改造点：现在这里不再写库，而是只发 Kafka 消息！快！
     */
    public void handleMatch(OrderBook.TradeMatch match) {
        // 组装消息对象
        TradeEvent event = new TradeEvent();
        event.setTakerId(match.getTakerId());
        event.setMakerId(match.getMakerId());
        event.setPrice(match.getPrice());
        event.setAmount(match.getAmount());
        event.setTimestamp(System.currentTimeMillis());
        // 注意：TradeMatch里没存symbol，实际项目中最好存一下，或者从orderBook里传过来
        event.setSymbol("BTC/USDT");

        // 发送消息到 MQ，完全异步，不阻塞引擎线程
        kafkaTemplate.send("trade-topic", JSON.toJSONString(event));
    }

    /**
     * 3. 结算逻辑 (Consumer 调用的方法)
     * 这里是原本 handleMatch 里的数据库操作逻辑，搬家到这里了。
     * 必须加事务！
     */
    @Transactional(rollbackFor = Exception.class)
    public void executeSettlement(TradeEvent event) {
        System.out.println(">>> 🏦 开始执行资金结算，价格: " + event.getPrice());

        // --- A. 更新订单状态 ---
        updateOrderProgress(event.getTakerId(), event.getAmount());
        updateOrderProgress(event.getMakerId(), event.getAmount());

        // --- B. 资金划转 ---
        // 重新查库，确保数据准确
        OrderEnt taker = orderMapper.selectById(event.getTakerId());
        OrderEnt maker = orderMapper.selectById(event.getMakerId());

        BigDecimal totalUSDT = event.getPrice().multiply(event.getAmount());
        BigDecimal amountCoin = event.getAmount();

        // 逻辑口诀：买家得币扣U，卖家得U扣币

        // 场景 1: Taker 是买家
        if ("BUY".equals(taker.getDirection())) {
            // 买家 (Taker): 获得 BTC, 扣除冻结 USDT
            transferAsset(taker.getUserId(), "BTC", amountCoin, BigDecimal.ZERO);
            transferAsset(taker.getUserId(), "USDT", BigDecimal.ZERO, totalUSDT.negate());

            // 卖家 (Maker): 获得 USDT, 扣除冻结 BTC
            transferAsset(maker.getUserId(), "USDT", totalUSDT, BigDecimal.ZERO);
            transferAsset(maker.getUserId(), "BTC", BigDecimal.ZERO, amountCoin.negate());
        }
        // 场景 2: Taker 是卖家
        else {
            // 卖家 (Taker): 获得 USDT, 扣除冻结 BTC
            transferAsset(taker.getUserId(), "USDT", totalUSDT, BigDecimal.ZERO);
            transferAsset(taker.getUserId(), "BTC", BigDecimal.ZERO, amountCoin.negate());

            // 买家 (Maker): 获得 BTC, 扣除冻结 USDT
            transferAsset(maker.getUserId(), "BTC", amountCoin, BigDecimal.ZERO);
            transferAsset(maker.getUserId(), "USDT", BigDecimal.ZERO, totalUSDT.negate());
        }
    }

    // --- 辅助方法 (和之前一样) ---

    private void updateOrderProgress(Long orderId, BigDecimal traded) {
        OrderEnt order = orderMapper.selectById(orderId);
        order.setTradedAmount(order.getTradedAmount().add(traded));
        // 简单判断完成状态
        if (order.getTradedAmount().compareTo(order.getAmount()) >= 0) {
            order.setStatus("COMPLETED");
        }
        orderMapper.updateById(order);
    }

    private void transferAsset(Long uid, String coin, BigDecimal balanceChange, BigDecimal frozenChange) {
        Wallet w = walletMapper.selectOne(new QueryWrapper<Wallet>().eq("user_id", uid).eq("coin", coin));
        if (w == null) {
            w = new Wallet();
            w.setUserId(uid);
            w.setCoin(coin);
            w.setBalance(BigDecimal.ZERO);
            w.setFrozen(BigDecimal.ZERO);
            walletMapper.insert(w);
        }
        w.setBalance(w.getBalance().add(balanceChange));
        w.setFrozen(w.getFrozen().add(frozenChange));
        walletMapper.updateById(w);
    }
}