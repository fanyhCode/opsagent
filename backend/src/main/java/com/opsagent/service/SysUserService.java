package com.opsagent.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.opsagent.common.BizException;
import com.opsagent.entity.SysUser;
import com.opsagent.mapper.SysUserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 用户业务层：负责注册与登录的业务规则。
 */
@Service
public class SysUserService {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;

    public SysUserService(SysUserMapper sysUserMapper, PasswordEncoder passwordEncoder) {
        this.sysUserMapper = sysUserMapper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 注册新用户。
     *
     * 业务规则：
     * 1. 用户名不能重复；
     * 2. 密码必须加密后再入库；
     * 3. 新注册用户默认是最低权限角色 VIEWER，由管理员后续调整。
     */
    public SysUser register(String username, String password, String nickname) {
        if (username == null || username.isBlank()) {
            throw new BizException("用户名不能为空");
        }
        if (password == null || password.length() < 6) {
            throw new BizException("密码长度不能少于 6 位");
        }

        // 用 LambdaQueryWrapper 构造查询条件，等价于 WHERE username = ?
        // 这种写法的好处是：字段名用方法引用表示，重构时 IDE 能检查出来，不会写错字符串
        Long existCount = sysUserMapper.selectCount(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (existCount != null && existCount > 0) {
            throw new BizException("用户名已存在");
        }

        SysUser user = new SysUser();
        user.setUsername(username);
        // 关键：存的是加密后的哈希，不是明文
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname(nickname == null || nickname.isBlank() ? username : nickname);
        user.setRole("VIEWER");
        user.setStatus(1);

        sysUserMapper.insert(user);
        return user;
    }

    /**
     * 登录校验。
     *
     * 安全细节：用户名不存在和密码错误都返回同一句提示，
     * 避免攻击者通过不同的报错"撞"出哪些用户名是存在的。
     */
    public SysUser login(String username, String password) {
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));

        if (user == null) {
            throw new BizException("用户名或密码错误");
        }
        // matches(明文, 数据库里的哈希) 由 BCrypt 内部完成校验
        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new BizException("用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BizException("账号已被禁用，请联系管理员");
        }
        return user;
    }
}
