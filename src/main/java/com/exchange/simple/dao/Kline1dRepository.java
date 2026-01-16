package com.exchange.simple.dao;

import com.exchange.simple.entity.Kline1d;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface Kline1dRepository extends MongoRepository<Kline1d, String> {}
