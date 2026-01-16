package com.exchange.simple.dao;

import com.exchange.simple.entity.Kline1m;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface Kline1mRepository extends MongoRepository<Kline1m, String> {
}

