package com.exchange.simple.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 用户实体类
 * 对应数据库表: user
 */
@Data
@TableName("user")
public class User {

    /**
     * 主键 ID (自增)
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 用户名 (唯一)
     */
    private String username;

    /**
     * 密码
     * 注意：MVP 版本为了演示方便存的是明文。
     * 生产环境必须存 Hash 值 (如 BCrypt加密)，绝对不能存明文！
     */
    private String password;
}