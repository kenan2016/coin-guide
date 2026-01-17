package com.exchange.simple.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.exchange.simple.entity.*;
import com.exchange.simple.mapper.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class ContractService {

    @Autowired private ContractWalletMapper walletMapper;
    @Autowired private ContractOrderMapper orderMapper;
    @Autowired private ContractPositionMapper positionMapper;
    // 假设你有一个类似现货的 ContractOrderBook 引擎
    // @Autowired private ContractEngine engine; 

    /**
     * 1. 合约下单 (Entry Point)
     * 作用：计算首付(起始保证金)，检查余额，冻结资金，推入撮合引擎
     *
     * @param direction BUY(做多/平空), SELL(做空/平多)
     * @param leverage 杠杆倍数
     */
    @Transactional(rollbackFor = Exception.class)
    public String placeOrder(Long userId, String symbol, String direction, 
                             BigDecimal price, BigDecimal amount, BigDecimal leverage) {
        
        // --- A. 计算资金 ---
        // 订单名义价值 = 价格 * 数量 (例如 50000 * 0.1 BTC = 5000 U)
        BigDecimal orderValue = price.multiply(amount);
        
        // 起始保证金 = 价值 / 杠杆 (例如 5000 / 10 = 500 U)
        BigDecimal requiredMargin = orderValue.divide(leverage, 4, RoundingMode.UP);

        // --- B. 检查并冻结资金 ---
        // 注意：这里简化逻辑，无论是开仓还是平仓，先冻结。
        // 真实交易所平仓单不仅不冻结钱，甚至可能释放保证金。为降低复杂度，MVP一律先冻结。
        ContractWallet wallet = walletMapper.selectOne(new QueryWrapper<ContractWallet>()
                .eq("user_id", userId).eq("coin", "USDT"));
        
        if (wallet == null || wallet.getBalance().compareTo(requiredMargin) < 0) {
            return "下单失败：合约账户保证金不足，请先划转";
        }

        // 扣减可用，增加冻结
        wallet.setBalance(wallet.getBalance().subtract(requiredMargin));
        wallet.setFrozen(wallet.getFrozen().add(requiredMargin));
        walletMapper.updateById(wallet);

        // --- C. 落库 ---
        ContractOrder order = new ContractOrder();
        order.setUserId(userId);
        order.setSymbol(symbol);
        order.setDirection(direction);
        order.setPrice(price);
        order.setAmount(amount);
        order.setLeverage(leverage);
        order.setStatus("PENDING");
        orderMapper.insert(order);

        // --- D. 推送撮合 (Mock) ---
        // engine.process(order);
        
        return "合约下单成功，冻结保证金: " + requiredMargin;
    }

    /**
     * 2. 撮合成功回调 (The Core Logic)
     * 作用：撮合引擎通知成交了，这里负责更新仓位 (Position)
     * * 逻辑分支：
     * 1. 没仓位 -> 建仓 (Open)
     * 2. 有仓位 & 方向相同 -> 加仓 (Add)
     * 3. 有仓位 & 方向相反 -> 减仓/平仓 (Reduce/Close)
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleMatch(Long userId, String symbol, String orderDirection, 
                            BigDecimal matchPrice, BigDecimal matchAmount, BigDecimal leverage) {
        
        // 1. 查找当前持仓
        ContractPosition pos = positionMapper.selectOne(new QueryWrapper<ContractPosition>()
                .eq("user_id", userId).eq("symbol", symbol));

        // 转换为仓位方向: BUY -> LONG, SELL -> SHORT
        String matchPosDirection = "BUY".equals(orderDirection) ? "LONG" : "SHORT";

        // ==============================
        // 场景一：新开仓 (Open Position)
        // ==============================
        if (pos == null) {
            pos = new ContractPosition();
            pos.setUserId(userId);
            pos.setSymbol(symbol);
            pos.setDirection(matchPosDirection);
            pos.setSize(matchAmount);
            pos.setEntryPrice(matchPrice);
            pos.setLeverage(leverage);
            
            // 保证金 = 成交价 * 数量 / 杠杆
            BigDecimal margin = matchPrice.multiply(matchAmount).divide(leverage, 4, RoundingMode.UP);
            pos.setMargin(margin);
            
            positionMapper.insert(pos);
            
            // 修正冻结余额：下单时可能冻多了(因为下单价和成交价不同)，多退少补逻辑略...
            // 简单做法：把冻结的钱直接扣掉作为 Margin (假设冻结=Margin)
            deductFrozen(userId, margin);
        } 
        
        // ==============================
        // 场景二：加仓 (Add Position)
        // ==============================
        else if (pos.getDirection().equals(matchPosDirection)) {
            // 1. 计算新均价 (加权平均)
            // 公式: (旧仓价值 + 新仓价值) / 总数量
            BigDecimal oldVal = pos.getSize().multiply(pos.getEntryPrice());
            BigDecimal newVal = matchAmount.multiply(matchPrice);
            BigDecimal totalSize = pos.getSize().add(matchAmount);
            
            BigDecimal avgPrice = oldVal.add(newVal).divide(totalSize, 8, RoundingMode.HALF_UP);
            
            // 2. 追加保证金
            BigDecimal addMargin = newVal.divide(pos.getLeverage(), 4, RoundingMode.UP);
            
            // 更新对象
            pos.setEntryPrice(avgPrice);
            pos.setSize(totalSize);
            pos.setMargin(pos.getMargin().add(addMargin)); // 保证金累加
            
            positionMapper.updateById(pos);
            deductFrozen(userId, addMargin);
        }
        
        // ==============================
        // 场景三：减仓/平仓 (Close Position)
        // ==============================
        else {
            // 此时：用户持 LONG，却 BUY 了一个 SHORT 方向的单 (即卖出平多)
            
            // 1. 计算盈亏 (PnL)
            // 做多平仓盈亏 = (平仓价 - 开仓价) * 数量
            // 做空平仓盈亏 = (开仓价 - 平仓价) * 数量
            BigDecimal pnl;
            if ("LONG".equals(pos.getDirection())) {
                pnl = matchPrice.subtract(pos.getEntryPrice()).multiply(matchAmount);
            } else {
                pnl = pos.getEntryPrice().subtract(matchPrice).multiply(matchAmount);
            }

            // 2. 计算按比例释放的保证金
            // 释放比例 = 平仓数量 / 持仓总量
            // 释放金 = 总保证金 * 释放比例
            BigDecimal releaseMargin = pos.getMargin().multiply(matchAmount)
                                      .divide(pos.getSize(), 4, RoundingMode.DOWN);

            // 3. 资金结算：返还给钱包 = 释放保证金 + 盈亏 (盈亏可能是负数)
            BigDecimal refundAmount = releaseMargin.add(pnl);
            refundToWallet(userId, refundAmount);

            // 4. 更新仓位
            BigDecimal newSize = pos.getSize().subtract(matchAmount);
            if (newSize.compareTo(BigDecimal.ZERO) <= 0) {
                // 仓位平光了，删除记录
                positionMapper.deleteById(pos.getId());
            } else {
                pos.setSize(newSize);
                pos.setMargin(pos.getMargin().subtract(releaseMargin));
                positionMapper.updateById(pos);
            }
            
            // 平仓单不需要扣冻结，直接把下单时的冻结解开即可 (略)
        }
    }

    // --- 资金辅助方法 ---
    
    private void deductFrozen(Long userId, BigDecimal amount) {
        ContractWallet w = walletMapper.selectOne(new QueryWrapper<ContractWallet>().eq("user_id", userId).eq("coin", "USDT"));
        w.setFrozen(w.getFrozen().subtract(amount)); 
        // 余额在下单时已经扣了，这里只扣冻结，意味着这笔钱正式变成了"持仓保证金"
        walletMapper.updateById(w);
    }
    
    private void refundToWallet(Long userId, BigDecimal amount) {
        ContractWallet w = walletMapper.selectOne(new QueryWrapper<ContractWallet>().eq("user_id", userId).eq("coin", "USDT"));
        w.setBalance(w.getBalance().add(amount)); // 钱回到余额
        // 注意：还需要处理下单时冻结的钱解冻，此处简化
        walletMapper.updateById(w);
    }
}