package com.exchange.simple.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.util.Date;

public class JwtUtils {

    // 密钥 (生产环境请写在配置文件里，且要很长很复杂)
    private static final String SECRET_KEY = "MySecretKey_For_Exchange_MVP_DoNotUseInProd";
    // 过期时间: 24小时 (毫秒)
    private static final long EXPIRE_TIME = 24 * 60 * 60 * 1000;

    /**
     * 生成 Token
     * @param userId 用户ID
     * @return 加密后的字符串
     */
    public static String generateToken(Long userId) {
        return Jwts.builder()
                .setSubject(userId.toString()) // 把 userId 存进 Token 主体
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + EXPIRE_TIME))
                .signWith(SignatureAlgorithm.HS256, SECRET_KEY) // 签名算法
                .compact();
    }

    /**
     * 解析 Token 获取 userId
     * @param token 加密字符串
     * @return userId (如果验证失败会抛出异常)
     */
    public static Long getUserIdFromToken(String token) {
        Claims claims = Jwts.parser()
                .setSigningKey(SECRET_KEY)
                .parseClaimsJws(token)
                .getBody();
        return Long.parseLong(claims.getSubject());
    }
}