package com.exchange.simple.config;

import com.exchange.simple.util.JwtUtils;
import com.exchange.simple.util.UserContext;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 获取 Header 中的 Token
        String authHeader = request.getHeader("Authorization");
        
        // 约定格式: "Bearer <token>"
        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7); // 去掉 "Bearer " 前缀
            try {
                // 2. 验证并解析 Token
                Long userId = JwtUtils.getUserIdFromToken(token);
                
                // 3. 存入 ThreadLocal，供后续 Controller 使用
                UserContext.setUserId(userId);
                return true; // 放行
            } catch (Exception e) {
                // Token 过期或被篡改
            }
        }

        // 4. 验证失败，返回 401 未授权
        response.setStatus(401);
        response.getWriter().write("Unauthorized: Please login first");
        return false; // 拦截
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        // 请求结束，清理 ThreadLocal，防止内存泄漏
        UserContext.remove();
    }
}