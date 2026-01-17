package com.exchange.simple.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.exchange.simple.entity.ContractPosition;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ContractPositionMapper extends BaseMapper<ContractPosition> {
    // 继承 BaseMapper 后，你就可以使用：
    // positionMapper.insert(pos);
    // positionMapper.updateById(pos);
    // positionMapper.selectOne(wrapper);
    // positionMapper.deleteById(id);
}