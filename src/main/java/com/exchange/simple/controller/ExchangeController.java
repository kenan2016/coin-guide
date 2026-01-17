package com.exchange.simple.controller;

import com.exchange.simple.service.TradeService;
import com.exchange.simple.util.UserContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api")
public class ExchangeController {

    @Autowired
    private TradeService tradeService;

    /**
     * 下单接口
     * 参数示例：
     * userId=1
     * symbol=BTC/USDT
     * direction=BUY
     * price=50000
     * amount=0.5
     */
    @PostMapping("/order")
    public String placeOrder(
            // @RequestParam Long userId,  <-- 删掉这个！
            @RequestParam String symbol,
            @RequestParam String direction,
            @RequestParam BigDecimal price,
            @RequestParam BigDecimal amount) {

        // 1. 从 ThreadLocal 获取当前登录用户的 ID
        Long currentUserId = UserContext.getUserId();

        // 2. 调用 Service
        return tradeService.placeOrder(currentUserId, symbol, direction, price, amount);
    }

    @PostMapping("/cancel")
    public String cancelOrder(Long orderId) {
        return tradeService.cancelOrder(orderId);
    }
}