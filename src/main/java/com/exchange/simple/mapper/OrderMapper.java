package com.exchange.simple.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.exchange.simple.entity.OrderEnt;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderMapper extends BaseMapper<OrderEnt> {
}
