package com.exchange.simple.consumer;

import com.alibaba.fastjson.JSON;
import com.exchange.simple.event.TradeEvent;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class MarketDataConsumer {

    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private SimpMessagingTemplate wsTemplate;

    /**
     * 监听撮合引擎发出的成交消息
     */
    @KafkaListener(topics = "trade-topic")
    public void onTrade(String message) {
        try {
            TradeEvent event = JSON.parseObject(message, TradeEvent.class);

            // 🔥 核心修改：同时更新三种周期的 K 线
            // 收到一笔成交，它既属于这一分钟，也属于这一小时，也属于这一天
            updateKline("1m", event);
            updateKline("1h", event);
            updateKline("1d", event);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 通用的 K 线更新逻辑
     *
     * @param period 周期名称: "1m", "1h", "1d"
     * @param event  成交事件
     *               <p>
     *               🔍 逻辑解析
     *               为什么可以同时更新？  假设现在是 2024-01-01 12:30:05，价格 50000。  这笔交易必然属于 12:30 的那根 1分钟 K 线。
     *               它也必然属于 12:00 的那根 1小时 K 线。
     *               它也必然属于 2024-01-01 的那根日线。
     *               所以，收到一笔消息，我们直接循环计算三次，更新三个 Redis Key 即可。
     *               时间戳取整算法 (timestamp / ms * ms)
     *               利用了整数除法的特性（丢弃小数）。
     *               举例：时间戳 12345，周期 100。
     *               12345 / 100 = 123 (整数)
     *               123 * 100 = 12300 (这就变成了 12300，即对齐到了 100 的倍数)。
     *               前端订阅
     *               现在前端可以根据用户选择的 Tab 订阅不同的频道：
     *               看分时图时订阅：/topic/kline/BTC/USDT/1m
     *               看日线图时订阅：/topic/kline/BTC/USDT/1d
     *               这样，你的 K 线系统就非常完善了！
     *               <p>
     *               1. WebSocket 推送格式 (单条更新)
     *               {
     *               "symbol": "BTC/USDT",     // 交易对
     *               "period": "1m",           // 周期：1分钟
     *               "open": "50000.00",       // 开盘价 (Open)
     *               "high": "50200.50",       // 最高价 (High)
     *               "low": "49800.00",        // 最低价 (Low)
     *               "close": "50150.20",      // 收盘价/最新价 (Close)
     *               "volume": "12.55",        // 成交量 (Volume - 基础币种 BTC)
     *               "amount": "629385.01",    // 成交额 (Quote Volume - 计价币种 USDT，可选)
     *               "startTime": 1705392000000, // K线起始时间戳 (毫秒)
     *               "endTime": 1705392059999    // K线结束时间戳 (毫秒)
     *               }
     *               <p>
     *
     *
     *
     *               2. REST API 历史数据格式 (数组压缩版)
     *               [
     *               // [时间, 开, 高, 低, 收, 量]
     *               [
     *               1705392000000, // Timestamp (时间戳)
     *               "50000.00",    // Open (开盘)
     *               "50200.50",    // High (最高)
     *               "49800.00",    // Low (最低)
     *               "50150.20",    // Close (收盘)
     *               "12.55"        // Volume (成交量)
     *               ],
     *               [
     *               1705392060000,
     *               "50150.20",
     *               "50300.00",
     *               "50100.00",
     *               "50250.00",
     *               "8.33"
     *               ]
     *               // ... 更多历史数据
     *               ]
     */
    private void updateKline(String period, TradeEvent event) {
        // 1. 计算该周期的起始时间 (关键算法)
        // 例如: 12:05:23 的成交
        // 1m -> 12:05:00
        // 1h -> 12:00:00
        // 1d -> 00:00:00
        long startTime = getPeriodStartTime(period, event.getTimestamp());

        // 2. 构造 Redis Key
        // 格式: kline:BTC/USDT:1m:1705392000000
        String redisKey = "kline:" + event.getSymbol() + ":" + period + ":" + startTime;

        // 3. 从 Redis 获取当前 K 线
        String klineJson = redisTemplate.opsForValue().get(redisKey);
        Kline kline;

        if (klineJson == null) {
            // 新的周期开始，初始化 K 线 (开高低收都等于当前价)
            kline = new Kline();
            kline.setSymbol(event.getSymbol());
            kline.setPeriod(period);
            kline.setOpen(event.getPrice());
            kline.setHigh(event.getPrice());
            kline.setLow(event.getPrice());
            kline.setClose(event.getPrice());
            kline.setVolume(event.getAmount());
            kline.setStartTime(startTime);
        } else {
            // 周期内已有数据，进行聚合更新
            kline = JSON.parseObject(klineJson, Kline.class);

            // High: 谁大取谁
            kline.setHigh(kline.getHigh().max(event.getPrice()));
            // Low: 谁小取谁
            kline.setLow(kline.getLow().min(event.getPrice()));
            // Close: 永远是最新的成交价
            kline.setClose(event.getPrice());
            // Volume: 累加成交量
            kline.setVolume(kline.getVolume().add(event.getAmount()));
        }

        // 4. 写回 Redis (设置过期时间，防止内存爆满，比如保留 2 天)
        // 实际生产中，会有另一个 ETL 任务负责把 Redis 数据持久化到 MongoDB
        redisTemplate.opsForValue().set(redisKey, JSON.toJSONString(kline));

        // 5. 推送 WebSocket
        // 订阅地址形如: /topic/kline/BTC/USDT/1m
        String wsTopic = "/topic/kline/" + event.getSymbol() + "/" + period;
        wsTemplate.convertAndSend(wsTopic, kline);

        // 日志调试
        // System.out.println(">>> 更新K线 [" + period + "]: " + kline);
    }

    /**
     * 辅助方法：根据当前时间戳和周期，计算 K 线柱子的起始时间
     */
    private long getPeriodStartTime(String period, long timestamp) {
        // 简单的时间戳取整算法 (适用于 UTC 时间)
        long ms = 0;
        switch (period) {
            case "1m":
                ms = 60 * 1000;      // 1分钟 = 60,000 毫秒
                break;
            case "1h":
                ms = 60 * 60 * 1000; // 1小时
                break;
            case "1d":
                ms = 24 * 60 * 60 * 1000; // 1天 (假设 UTC+0)
                break;
            default:
                throw new RuntimeException("不支持的周期");
        }
        // 核心算法: 去掉余数
        return (timestamp / ms) * ms;
    }

    // 内部类 K线数据结构
    @Data
    public static class Kline {
        private String symbol;
        private String period; // 1m, 1h, 1d
        private BigDecimal open;
        private BigDecimal high;
        private BigDecimal low;
        private BigDecimal close;
        private BigDecimal volume;
        private Long startTime; // K线开始时间戳
    }
}