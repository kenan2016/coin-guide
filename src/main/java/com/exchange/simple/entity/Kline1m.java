package com.exchange.simple.entity;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "kline_1m")
@CompoundIndex(name = "idx_symbol_time", def = "{'symbol': 1, 'startTime': -1}")
public class Kline1m extends BaseKline {}
