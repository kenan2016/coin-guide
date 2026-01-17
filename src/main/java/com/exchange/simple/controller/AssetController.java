package com.exchange.simple.controller;

import com.exchange.simple.service.AssetService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.math.BigDecimal;

/**
 * 资产管理接口
 * 负责处理充值入账和提现请求
 */
@RestController
@RequestMapping("/api/asset")
public class AssetController {

    @Autowired private AssetService assetService;

    /**
     * [MOCK] 模拟充值接口
     * 注意：生产环境中，这个接口不对外暴露，而是由内部的区块链监听服务调用。
     * * URL: POST /api/asset/deposit
     * 参数: userId=1, coin=USDT, amount=1000
     */
    @PostMapping("/deposit")
    public String deposit(Long userId, String coin, BigDecimal amount) {
        return assetService.deposit(userId, coin, amount);
    }

    /**
     * 申请提现接口
     * 用户在前端点击"提现"按钮时调用此接口。
     * * URL: POST /api/asset/withdraw
     * 参数: userId=1, coin=USDT, amount=100, address=0x123456...
     */
    @PostMapping("/withdraw")
    public String withdraw(Long userId, String coin, BigDecimal amount, String address) {
        return assetService.requestWithdraw(userId, coin, amount, address);
    }

    /**
     * [MOCK] 管理员审核接口
     * 模拟后台管理系统中，管理员点击"通过"或"拒绝"按钮。
     * * URL: POST /api/asset/audit
     * 参数: txId=10 (流水ID), pass=true (是否通过)
     */
    @PostMapping("/audit")
    public String audit(Long txId, boolean pass) {
        return assetService.auditWithdraw(txId, pass);
    }
}