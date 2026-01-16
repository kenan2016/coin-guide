package com.exchange.simple.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.math.BigDecimal;

/**
 * K线数据父类
 * 定义通用字段
 */
@Data
public abstract class BaseKline {
    
    @Id
    private String id; // Mongo 自动生成的 ID

    // 建立联合索引：symbol + startTime，加速查询
    // 注意：具体索引要在子类上的 @CompoundIndex 注解中定义
    
    private String symbol;      // BTC/USDT
    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal close;
    private BigDecimal volume;
    
    @Field("t") // 在 Mongo 里存成短字段名 "t"，节省空间
    private Long startTime; 
}

// === 下面是不同的集合映射 ===

