package com.exchange.simple.util;

public class UserContext {
    // 每个线程独立的存储空间
    private static final ThreadLocal<Long> userHolder = new ThreadLocal<>();

    public static void setUserId(Long userId) {
        userHolder.set(userId);
    }

    public static Long getUserId() {
        return userHolder.get();
    }

    public static void remove() {
        userHolder.remove(); // 防止内存泄漏
    }
}