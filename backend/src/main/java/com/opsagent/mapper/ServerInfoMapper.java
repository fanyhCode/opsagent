package com.opsagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.opsagent.entity.ServerInfo;

/**
 * 服务器信息的数据访问接口（Mapper / DAO 层）。
 *
 * 我们只声明一个接口并继承 BaseMapper，MyBatis-Plus 会自动生成实现，
 * 并提供 selectList / selectById / insert / updateById / deleteById 等常用方法，
 * 单表增删改查不用再手写 SQL。复杂查询才需要在接口里另外定义方法并编写 SQL。
 */
public interface ServerInfoMapper extends BaseMapper<ServerInfo> {
}
