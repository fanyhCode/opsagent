package com.opsagent.service;

import com.opsagent.entity.ServerInfo;
import com.opsagent.mapper.ServerInfoMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 服务器信息的业务层（Service）。
 *
 * 为什么要单独有一层 Service，而不是让 Controller 直接调用 Mapper？
 * 1. 分层清晰：Controller 只管接收请求、返回结果；业务规则集中在 Service；
 * 2. 便于复用：同一段业务逻辑可以被多个接口调用；
 * 3. 便于测试：可以对 Service 单独写单元测试。
 *
 * @Service 把这个类交给 Spring 管理，其他地方需要时由 Spring 注入（依赖注入）。
 */
@Service
public class ServerInfoService {

    private final ServerInfoMapper serverInfoMapper;

    /**
     * 构造器注入：Spring 启动时会自动把 ServerInfoMapper 的实现传进来。
     * 用构造器注入而不是字段注入，是为了让依赖关系一目了然，也便于写单元测试。
     */
    public ServerInfoService(ServerInfoMapper serverInfoMapper) {
        this.serverInfoMapper = serverInfoMapper;
    }

    /**
     * 查询所有服务器。
     * selectList(null) 表示不加任何查询条件，等价于 SELECT * FROM server。
     */
    public List<ServerInfo> listAll() {
        return serverInfoMapper.selectList(null);
    }

    /**
     * 按 id 查询单台服务器，查不到返回 null。
     * 监控、SSH 执行等模块都需要先拿到服务器信息（含主机地址与登录凭据）。
     */
    public ServerInfo getById(Long id) {
        return serverInfoMapper.selectById(id);
    }
}
