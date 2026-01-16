package com.exchange.simple.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.exchange.simple.entity.*;
import com.exchange.simple.mapper.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TradeService {

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
     * 撮合成功后的结算逻辑 (回调函数)
     * 注意: 这个方法是在 synchronized 块中被调用的，属于事务的一部分
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleMatch(OrderBook.TradeMatch match) {
        System.out.println(">>> ⚡️ 撮合成功，开始结算: " + match);

        // --- 1. 更新订单进度 ---
        updateOrderProgress(match.getTakerId(), match.getAmount());
        updateOrderProgress(match.getMakerId(), match.getAmount());

        // --- 2. 资金结算 (核心难点) ---
        // 重新查库获取买卖双方的完整信息
        OrderEnt taker = orderMapper.selectById(match.getTakerId());
        OrderEnt maker = orderMapper.selectById(match.getMakerId());

        // 计算本次成交的总金额 (USDT)
        BigDecimal totalUSDT = match.getPrice().multiply(match.getAmount());

        // 场景 A: Taker 是买家 (主动买入)
        if ("BUY".equals(taker.getDirection())) {
            // 买方(Taker): 获得 BTC, 扣除冻结的 USDT
            transferAsset(taker.getUserId(), "BTC", match.getAmount(), BigDecimal.ZERO);
            transferAsset(taker.getUserId(), "USDT", BigDecimal.ZERO, totalUSDT.negate());

            // 卖方(Maker): 获得 USDT, 扣除冻结的 BTC
            transferAsset(maker.getUserId(), "USDT", totalUSDT, BigDecimal.ZERO);
            transferAsset(maker.getUserId(), "BTC", BigDecimal.ZERO, match.getAmount().negate());
        }
        // 场景 B: Taker 是卖家 (主动卖出)
        else {
            // 卖方(Taker): 获得 USDT, 扣除冻结的 BTC
            transferAsset(taker.getUserId(), "USDT", totalUSDT, BigDecimal.ZERO);
            transferAsset(taker.getUserId(), "BTC", BigDecimal.ZERO, match.getAmount().negate());

            // 买方(Maker): 获得 BTC, 扣除冻结的 USDT
            transferAsset(maker.getUserId(), "BTC", match.getAmount(), BigDecimal.ZERO);
            transferAsset(maker.getUserId(), "USDT", BigDecimal.ZERO, totalUSDT.negate());
        }
    }

    /**
     * 辅助方法: 更新数据库中订单的成交量和状态
     */
    private void updateOrderProgress(Long orderId, BigDecimal traded) {
        OrderEnt order = orderMapper.selectById(orderId);
        // 累加成交量
        order.setTradedAmount(order.getTradedAmount().add(traded));

        // 判断是否全部成交 (简单判断: 已成交 >= 初始总量)
        // 注意: 这里的 getAmount 在数据库里存的是初始总量，而在内存 OrderBook 里会被修改为剩余量。
        // 为了简化 MVP，我们假设这里读取的是 DB 里的原始快照。
        // 在严谨的生产系统中，order表会有 total_amount 和 remain_amount 两个字段。
        // 这里做一个简化的假设：如果 tradedAmount 非常接近初始 amount，就认为是完成了。
        // 实际上 MVP 代码里 OrderBook 修改的是内存对象的 amount，不影响 DB 读取。
        // 我们通过查询 DB 里的 amount (初始值) 和 tradedAmount 比较：
        if (order.getTradedAmount().compareTo(order.getAmount()) >= 0) {
            order.setStatus("COMPLETED");
        }
        orderMapper.updateById(order);
    }

    /**
     * 辅助方法: 资产划转通用逻辑
     *
     * @param balanceChange: 正数表示增加余额，0表示不变
     * @param frozenChange:  负数表示扣除冻结，0表示不变
     */
    private void transferAsset(Long uid, String coin, BigDecimal balanceChange, BigDecimal frozenChange) {
        Wallet w = walletMapper.selectOne(new QueryWrapper<Wallet>().eq("user_id", uid).eq("coin", coin));
        if (w == null) {
            // 如果钱包不存在（比如买方以前没买过BTC，第一次买），新建一个
            w = new Wallet();
            w.setUserId(uid);
            w.setCoin(coin);
            w.setBalance(BigDecimal.ZERO);
            w.setFrozen(BigDecimal.ZERO);
            walletMapper.insert(w);
        }
        // 更新余额和冻结
        w.setBalance(w.getBalance().add(balanceChange));
        w.setFrozen(w.getFrozen().add(frozenChange));
        walletMapper.updateById(w);
    }
}