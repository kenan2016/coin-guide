package com.exchange.simple.etl;

import com.exchange.simple.entity.ContractPosition;
import com.exchange.simple.mapper.ContractPositionMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class RiskEngine {

    @Autowired private ContractPositionMapper positionMapper;
    // 假设我们有一个 Redis 用于获取最新市场价
    // @Autowired private RedisService redisService; 

    // 维持保证金率 (0.5%)
    // 当 (保证金 + 浮动盈亏) < (持仓价值 * 0.5%) 时，爆仓
    private final BigDecimal MAINT_MARGIN_RATE = new BigDecimal("0.005");

    /**
     * 每 500ms 扫描全场
     */
    @Scheduled(fixedRate = 500)
    public void scanLiquidation() {
        // 1. 获取当前标记价格 (Mark Price)
        // 真实场景从 Redis 获取，这里 Mock 一个价格
        BigDecimal currentPrice = new BigDecimal("50000"); 

        // 2. 查出所有活跃仓位 (实际需分页处理)
        List<ContractPosition> positions = positionMapper.selectList(null);

        for (ContractPosition pos : positions) {
            try {
                checkPositionRisk(pos, currentPrice);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void checkPositionRisk(ContractPosition pos, BigDecimal currentPrice) {
        // 1. 计算浮动盈亏 (Unrealized PnL)
        BigDecimal pnl;
        if ("LONG".equals(pos.getDirection())) {
            // 多单盈亏 = (现价 - 均价) * 数量
            pnl = currentPrice.subtract(pos.getEntryPrice()).multiply(pos.getSize());
        } else {
            // 空单盈亏 = (均价 - 现价) * 数量
            pnl = pos.getEntryPrice().subtract(currentPrice).multiply(pos.getSize());
        }

        // 2. 计算当前权益 (Equity)
        // 权益 = 你的保证金 + 浮动盈亏 (如果是亏损，pnl为负)
        BigDecimal equity = pos.getMargin().add(pnl);

        // 3. 计算维持保证金线 (Maintenance Margin)
        // 维持线 = 当前持仓价值 * 0.005
        BigDecimal positionValue = currentPrice.multiply(pos.getSize());
        BigDecimal maintenanceMargin = positionValue.multiply(MAINT_MARGIN_RATE);

        // 4. 判断生死
        // 如果 权益 < 维持线 -> 爆仓！
        if (equity.compareTo(maintenanceMargin) < 0) {
            executeLiquidation(pos);
        }
    }

    private void executeLiquidation(ContractPosition pos) {
        System.out.println("💀 触发强平!!! 用户ID: " + pos.getUserId());
        
        // 1. 简单处理：直接把仓位删掉 (Position Close)
        // 真实处理：系统接管挂市价平仓单，剩余资金注入风险基金
        positionMapper.deleteById(pos.getId());
        
        // 2. 保证金归零 (被没收)
        // 此时钱不退给 ContractWallet，相当于被系统吃掉了
        
        // 3. 发短信/邮件通知用户
    }
}