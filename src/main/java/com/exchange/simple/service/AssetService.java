package com.exchange.simple.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.exchange.simple.entity.Transaction;
import com.exchange.simple.entity.Wallet;
import com.exchange.simple.mapper.TransactionMapper;
import com.exchange.simple.mapper.WalletMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class AssetService {

    @Autowired private WalletMapper walletMapper;
    @Autowired private TransactionMapper transactionMapper;

    /**
     * 业务场景: 充值 (Deposit)
     * 触发时机: 通常由“区块链监听服务”监听到链上转账后调用，或者管理员后台补单。
     * * @param userId 用户ID
     * @param coin 币种
     * @param amount 金额
     * @return 结果描述
     */
    @Transactional(rollbackFor = Exception.class) // 开启事务：必须保证“记流水”和“加余额”同时成功
    public String deposit(Long userId, String coin, BigDecimal amount) {
        
        // 1. 模拟生成链上 TX Hash
        // 在真实生产环境中，这个 hash 应该是作为参数传进来的 (例如: 0x88df...)
        String txHash = "0x" + UUID.randomUUID().toString().replace("-", "");

        // 2. 🔥 核心安全检查：幂等性 (Idempotency)
        // 为什么？因为区块链网络不稳定，监听服务可能会把同一笔转账推送多次。
        // 我们必须检查数据库里是否已经处理过这个 txHash。
        Long count = transactionMapper.selectCount(new QueryWrapper<Transaction>().eq("tx_hash", txHash));
        if (count > 0) {
            return "充值失败：该交易已处理，请勿重复入账";
        }

        // 3. 记录流水 (留痕)
        Transaction tx = new Transaction();
        tx.setUserId(userId);
        tx.setCoin(coin);
        tx.setAmount(amount);
        tx.setType("DEPOSIT");
        tx.setStatus("SUCCESS"); // 充值只要进来了，通常直接标记成功
        tx.setTxHash(txHash);    // 记录 Hash 以便下次排重
        transactionMapper.insert(tx);

        // 4. 执行加钱逻辑
        addBalance(userId, coin, amount);

        return "充值成功，流水ID: " + tx.getId();
    }

    /**
     * 业务场景: 用户发起提现申请 (Withdraw Request)
     * 逻辑特点: "先扣款，后审核"。
     * 为什么先扣款？防止用户在审核期间把这笔钱拿去交易或再次提现（双花攻击）。
     */
    @Transactional(rollbackFor = Exception.class)
    public String requestWithdraw(Long userId, String coin, BigDecimal amount, String address) {
        
        // 1. 检查余额并直接扣除
        // 注意：这里我们直接扣除 balance，而不是 frozen。
        // 有些交易所逻辑是：balance -> frozen，审核通过后 frozen -> 消失。
        // 这里为了简化 MVP，直接扣除。如果审核失败，再加回来。
        boolean deductSuccess = deductBalance(userId, coin, amount);
        if (!deductSuccess) {
            return "提现失败：可用余额不足";
        }

        // 2. 记录流水 (状态为 PENDING)
        // PENDING 代表这笔钱已经从用户账上消失了，但还没发到链上，正在等待管理员点头。
        Transaction tx = new Transaction();
        tx.setUserId(userId);
        tx.setCoin(coin);
        tx.setAmount(amount);
        tx.setType("WITHDRAW");
        tx.setStatus("PENDING"); 
        tx.setAddress(address);
        transactionMapper.insert(tx);

        return "提现申请已提交，等待审核。流水ID: " + tx.getId();
    }

    /**
     * 业务场景: 管理员审核提现 (Audit)
     * 逻辑特点: 决定资金是真正流出，还是退回给用户。
     *
     * @param txId 流水表的主键ID
     * @param pass true=通过, false=拒绝
     */
    @Transactional(rollbackFor = Exception.class)
    public String auditWithdraw(Long txId, boolean pass) {
        // 1. 查单
        Transaction tx = transactionMapper.selectById(txId);
        // 必须确保订单存在，且状态是 PENDING (防止重复审核已经成功的订单)
        if (tx == null || !"PENDING".equals(tx.getStatus())) {
            return "审核失败：订单不存在或状态不正确";
        }

        if (pass) {
            // --- 分支 A: 审核通过 ---
            tx.setStatus("SUCCESS");
            
            // 模拟打币：
            // 在真实系统中，这里会调用 WalletNode 的 RPC 接口 (如 sendToAddress)。
            // 获得链上返回的 hash 后，更新到数据库。
            String mockOnChainHash = "0x_MOCK_WITHDRAW_" + System.currentTimeMillis();
            tx.setTxHash(mockOnChainHash);
            
            transactionMapper.updateById(tx);
            return "审核通过，已向区块链广播交易，Hash: " + mockOnChainHash;
        } else {
            // --- 分支 B: 审核拒绝 ---
            tx.setStatus("FAILED");
            transactionMapper.updateById(tx);
            
            // 🔥 关键逻辑：冲正 (Refund)
            // 既然不让提现，必须把第一步扣掉的钱退回给用户。
            addBalance(tx.getUserId(), tx.getCoin(), tx.getAmount());
            
            return "审核拒绝，资金已退回用户余额";
        }
    }

    // --- 内部通用方法 ---

    /**
     * 通用加钱方法
     * 兼容了“老用户加钱”和“新用户首次开户”两种情况
     */
    private void addBalance(Long userId, String coin, BigDecimal amount) {
        Wallet wallet = walletMapper.selectOne(new QueryWrapper<Wallet>()
                .eq("user_id", userId).eq("coin", coin));
        
        if (wallet == null) {
            // 如果是新币种，插入新记录
            wallet = new Wallet(); 
            wallet.setUserId(userId); 
            wallet.setCoin(coin);
            wallet.setBalance(BigDecimal.ZERO); 
            wallet.setFrozen(BigDecimal.ZERO);
            walletMapper.insert(wallet);
        }
        
        // 更新余额
        wallet.setBalance(wallet.getBalance().add(amount));
        walletMapper.updateById(wallet);
    }

    /**
     * 通用扣钱方法
     * @return true=扣款成功, false=余额不足
     */
    private boolean deductBalance(Long userId, String coin, BigDecimal amount) {
        Wallet wallet = walletMapper.selectOne(new QueryWrapper<Wallet>()
                .eq("user_id", userId).eq("coin", coin));
        
        // 余额检查
        if (wallet == null || wallet.getBalance().compareTo(amount) < 0) {
            return false;
        }
        
        // 执行扣款
        wallet.setBalance(wallet.getBalance().subtract(amount));
        walletMapper.updateById(wallet);
        return true;
    }
}