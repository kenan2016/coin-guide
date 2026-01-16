package com.exchange.simple.dao;

import com.exchange.simple.entity.Kline1h;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface Kline1hRepository extends MongoRepository<Kline1h, String> {}
