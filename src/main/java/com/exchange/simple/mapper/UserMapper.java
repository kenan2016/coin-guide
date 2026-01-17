package com.exchange.simple.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.exchange.simple.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户数据访问层
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
    // 继承 BaseMapper 后，自动拥有 CRUD 能力
    // 无需编写 XML 文件
}