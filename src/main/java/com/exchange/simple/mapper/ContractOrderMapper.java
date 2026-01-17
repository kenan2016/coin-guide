package com.exchange.simple.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.exchange.simple.entity.ContractOrder;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ContractOrderMapper extends BaseMapper<ContractOrder> {
    // 同样直接继承 BaseMapper
    // 常用的方法：
    // selectList(new QueryWrapper<ContractOrder>().eq("user_id", uid).eq("status", "PENDING"));
}