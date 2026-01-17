package com.exchange.simple.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.exchange.simple.entity.ContractWallet;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

@Mapper
public interface ContractWalletMapper extends BaseMapper<ContractWallet> {
    
    // MyBatis Plus 自带了 insert, updateById, selectOne 等方法
    // 直接继承即可使用

    /**
     * 【进阶补充】原子性更新余额 (推荐生产环境使用)
     * 避免并发情况下 Java 层面的 "读-改-写" 导致余额覆盖问题
     */
    @Update("UPDATE contract_wallet SET balance = balance + #{amount} " +
            "WHERE user_id = #{userId} AND coin = #{coin}")
    int increaseBalance(@Param("userId") Long userId, 
                        @Param("coin") String coin, 
                        @Param("amount") BigDecimal amount);

    @Update("UPDATE contract_wallet SET balance = balance - #{amount}, frozen = frozen + #{amount} " +
            "WHERE user_id = #{userId} AND coin = #{coin} AND balance >= #{amount}")
    int freezeBalance(@Param("userId") Long userId, 
                      @Param("coin") String coin, 
                      @Param("amount") BigDecimal amount);
}