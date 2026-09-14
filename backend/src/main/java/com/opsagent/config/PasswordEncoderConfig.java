package com.opsagent.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码加密器配置。
 *
 * 为什么密码必须加密存储？
 * 一旦数据库泄露，明文密码会让攻击者直接登录所有账号（用户往往在多处使用同一密码）。
 * 业界标准做法是存"哈希值"，而且要用专门为抗暴力破解设计的算法。
 *
 * BCrypt 的三个关键特性：
 * 1. 单向不可逆：只能验证，不能还原出原密码；
 * 2. 自带随机盐：同一个密码每次加密结果都不同，攻击者无法用彩虹表批量破解；
 * 3. 故意"算得慢"：可通过强度参数调节，让暴力枚举的代价极高。
 */
@Configuration
public class PasswordEncoderConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
