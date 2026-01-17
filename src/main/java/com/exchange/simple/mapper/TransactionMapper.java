package com.exchange.simple.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.exchange.simple.entity.Transaction;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TransactionMapper extends BaseMapper<Transaction> {}