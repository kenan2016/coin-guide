package com.exchange.simple;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.exchange.simple.entity.Wallet;
import com.exchange.simple.mapper.OrderMapper;
import com.exchange.simple.mapper.WalletMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@AutoConfigureMockMvc // 开启 MockMvc 用于模拟 HTTP 请求
public class ExchangeFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WalletMapper walletMapper;

    @Autowired
    private OrderMapper orderMapper;

    // 定义测试用户ID
    private final Long buyerId = 100L;
    private final Long sellerId = 200L;

    @BeforeEach
    public void setup() {
        // 1. 清理旧数据 (防止之前的测试干扰)
        walletMapper.delete(new QueryWrapper<>());
        orderMapper.delete(new QueryWrapper<>());

        // 2. 初始化资金
        // 买家: 有 50000 USDT, 0 BTC
        createWallet(buyerId, "USDT", new BigDecimal("50000"));
        createWallet(buyerId, "BTC", BigDecimal.ZERO);

        // 卖家: 有 1 BTC, 0 USDT
        createWallet(sellerId, "BTC", new BigDecimal("1"));
        createWallet(sellerId, "USDT", BigDecimal.ZERO);
    }

    @Test
    public void testLimitOrderMatching() throws Exception {
        System.out.println("=== 测试开始: 模拟 卖家挂单 -> 买家吃单 ===");

        // --- 步骤 1: 卖家(ID 200) 挂卖单 ---
        // 卖 1 BTC, 价格 50000 USDT
        mockMvc.perform(post("/api/order")
                        .param("userId", sellerId.toString())
                        .param("symbol", "BTC/USDT")
                        .param("direction", "SELL")
                        .param("price", "50000")
                        .param("amount", "1"))
                .andExpect(status().isOk())
                .andDo(print()); // 打印请求日志

        // 验证中间状态: 卖家的 BTC 应该被冻结
        Wallet sellerBtc = getWallet(sellerId, "BTC");
        assertEquals(0, sellerBtc.getBalance().compareTo(BigDecimal.ZERO), "卖家可用余额应为0");
        assertEquals(0, sellerBtc.getFrozen().compareTo(new BigDecimal("1")), "卖家冻结余额应为1");


        // --- 步骤 2: 买家(ID 100) 挂买单 (吃单) ---
        // 买 1 BTC, 价格 50000 USDT
        mockMvc.perform(post("/api/order")
                        .param("userId", buyerId.toString())
                        .param("symbol", "BTC/USDT")
                        .param("direction", "BUY")
                        .param("price", "50000")
                        .param("amount", "1"))
                .andExpect(status().isOk())
                .andDo(print());

        // --- 步骤 3: 验证最终结算结果 ---
        
        // 验证买家 (预期: USDT -50000, BTC +1)
        Wallet buyerUsdt = getWallet(buyerId, "USDT");
        Wallet buyerBtc = getWallet(buyerId, "BTC");
        // 使用 doubleValue 简化断言比较
        assertEquals(0.0, buyerUsdt.getBalance().doubleValue(), "买家USDT应该花光了");
        assertEquals(1.0, buyerBtc.getBalance().doubleValue(), "买家应该收到了1个BTC");

        // 验证卖家 (预期: BTC -1, USDT +50000)
        Wallet sellerUsdt = getWallet(sellerId, "USDT");
        sellerBtc = getWallet(sellerId, "BTC"); // 重新获取
        assertEquals(0.0, sellerBtc.getFrozen().doubleValue(), "卖家BTC冻结应该被扣除");
        assertEquals(50000.0, sellerUsdt.getBalance().doubleValue(), "卖家应该收到了50000 USDT");

        System.out.println("=== 测试通过: 撮合成功，资金结算正确 ===");
    }

    // 辅助方法: 创建钱包
    private void createWallet(Long uid, String coin, BigDecimal balance) {
        Wallet w = new Wallet();
        w.setUserId(uid);
        w.setCoin(coin);
        w.setBalance(balance);
        w.setFrozen(BigDecimal.ZERO);
        walletMapper.insert(w);
    }

    // 辅助方法: 查询钱包
    private Wallet getWallet(Long uid, String coin) {
        return walletMapper.selectOne(new QueryWrapper<Wallet>().eq("user_id", uid).eq("coin", coin));
    }
}