package com.exchange.simple.etl;

import com.alibaba.fastjson.JSON;
import com.exchange.simple.consumer.MarketDataConsumer;

import com.exchange.simple.dao.Kline1dRepository;
import com.exchange.simple.dao.Kline1hRepository;
import com.exchange.simple.dao.Kline1mRepository;
import com.exchange.simple.entity.Kline1d;
import com.exchange.simple.entity.Kline1h;
import com.exchange.simple.entity.Kline1m;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;

@Component
public class KlinePersistenceTask {

    @Autowired private StringRedisTemplate redisTemplate;
    @Autowired private Kline1mRepository kline1mRepo;
    @Autowired private Kline1hRepository kline1hRepo;
    @Autowired private Kline1dRepository kline1dRepo;

    /**
     * 定时任务：每分钟的第 0 秒执行一次
     * 逻辑：
     * 1. 总是保存上一分钟的 1m 线。
     * 2. 如果当前是整点，保存上一小时的 1h 线。
     * 3. 如果当前是 0点，保存昨天的 1d 线。
     */
    @Scheduled(cron = "0 * * * * *") 
    public void saveKline() {
        LocalDateTime now = LocalDateTime.now(ZoneId.of("UTC")); // 建议统一用 UTC 时间
        int minute = now.getMinute();
        int hour = now.getHour();

        String symbol = "BTC/USDT"; // 实际需遍历所有币种

        // === 1. 处理 1m 线 (每分钟都做) ===
        savePeriod(symbol, "1m", now.minusMinutes(1)); // 取上一分钟的时间

        // === 2. 处理 1h 线 (整点执行) ===
        if (minute == 0) {
            System.out.println(">>> 整点到了，开始归档 1h K线");
            savePeriod(symbol, "1h", now.minusHours(1)); // 取上一小时的时间
        }

        // === 3. 处理 1d 线 (0点执行) ===
        if (hour == 0 && minute == 0) {
            System.out.println(">>> 0点到了，开始归档 日K线");
            savePeriod(symbol, "1d", now.minusDays(1)); // 取昨天的时间
        }
    }

    private void savePeriod(String symbol, String period, LocalDateTime timeObj) {
        // 1. 计算 Redis Key 的时间戳后缀
        long targetTime = 0;
        if ("1m".equals(period)) {
            // 取那分钟的 00秒
            targetTime = timeObj.withSecond(0).atZone(ZoneId.of("UTC")).toInstant().toEpochMilli();
        } else if ("1h".equals(period)) {
            // 取那小时的 00分00秒
            targetTime = timeObj.withMinute(0).withSecond(0).atZone(ZoneId.of("UTC")).toInstant().toEpochMilli();
        } else if ("1d".equals(period)) {
            // 取那天的 00:00:00
            targetTime = timeObj.toLocalDate().atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli();
        }

        // 2. 从 Redis 读取
        String redisKey = "kline:" + symbol + ":" + period + ":" + targetTime;
        String json = redisTemplate.opsForValue().get(redisKey);

        if (json != null) {
            // 3. 转换为 Java 对象
            MarketDataConsumer.Kline redisKline = JSON.parseObject(json, MarketDataConsumer.Kline.class);
            
            // 4. 保存到 MongoDB 不同的集合
            if ("1m".equals(period)) {
                Kline1m entity = new Kline1m();
                BeanUtils.copyProperties(redisKline, entity);
                kline1mRepo.save(entity);
            } else if ("1h".equals(period)) {
                Kline1h entity = new Kline1h();
                BeanUtils.copyProperties(redisKline, entity);
                kline1hRepo.save(entity);
            } else if ("1d".equals(period)) {
                Kline1d entity = new Kline1d();
                BeanUtils.copyProperties(redisKline, entity);
                kline1dRepo.save(entity);
            }
            
            System.out.println("✅ 已归档 " + period + " K线: " + redisKey);
            
            // 5. 归档后，删除 Redis 里的旧数据以释放内存
            redisTemplate.delete(redisKey);
        }
    }
}