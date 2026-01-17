package com.exchange.simple.engin.disruptor;

import com.exchange.simple.entity.OrderEnt;
import com.exchange.simple.service.TradeService;
import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

@Component
public class DisruptorEngine {

    // 缓冲区大小，必须是 2 的 N 次方
    private static final int BUFFER_SIZE = 1024 * 1024; 

    private Disruptor<OrderEvent> disruptor;
    private RingBuffer<OrderEvent> ringBuffer;
    
    // 循环依赖解决：Engine 需要调 TradeService 回调，TradeService 需要调 Engine 发单
    // 使用 @Lazy 延迟加载
    private final TradeService tradeService;

    public DisruptorEngine(@Lazy TradeService tradeService) {
        this.tradeService = tradeService;
    }

    @PostConstruct
    public void start() {
        // 1. 线程工厂 (给消费者线程起个名字)
        ThreadFactory threadFactory = r -> new Thread(r, "Disruptor-Match-Thread");

        // 2. 创建 Disruptor
        // ProducerType.MULTI: 支持多个线程同时往里塞订单 (Http 接口是多线程的)
        // BlockingWaitStrategy: 消费者等待策略，Blocking 最稳，Yielding 性能更高但烧 CPU
        disruptor = new Disruptor<>(
                new OrderEventFactory(),
                BUFFER_SIZE,
                threadFactory,
                ProducerType.MULTI,
                new BlockingWaitStrategy()
        );

        // 3. 连接消费者
        disruptor.handleEventsWith(new MatchEventHandler(tradeService));

        // 4. 启动
        disruptor.start();
        ringBuffer = disruptor.getRingBuffer();
        
        System.out.println("🚀 Disruptor 撮合引擎已启动，RingBuffer 大小: " + BUFFER_SIZE);
    }

    @PreDestroy
    public void stop() {
        if (disruptor != null) {
            disruptor.shutdown();
        }
    }

    /**
     * 外部调用的入口：发布订单到队列
     */
    public void onOrder(OrderEnt order) {
        // 1. 获取下一个序号
        long sequence = ringBuffer.next();
        try {
            // 2. 获取该序号对应的空事件
            OrderEvent event = ringBuffer.get(sequence);
            // 3. 填充数据
            event.setOrder(order);
        } finally {
            // 4. 发布 (必须在 finally 里，否则 RingBuffer 会卡死)
            ringBuffer.publish(sequence);
        }
    }
}