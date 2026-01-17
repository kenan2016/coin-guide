package com.exchange.simple.controller;

import com.exchange.simple.service.TradeService;
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
            @RequestParam Long userId,
            @RequestParam String symbol,
            @RequestParam String direction,
            @RequestParam BigDecimal price,
            @RequestParam BigDecimal amount) {

        try {
            return tradeService.placeOrder(userId, symbol, direction, price, amount);
        } catch (Exception e) {
            e.printStackTrace();
            return "Error: " + e.getMessage();
        }
    }

    @PostMapping("/cancel")
    public String cancelOrder(Long orderId) {
        return tradeService.cancelOrder(orderId);
    }
}