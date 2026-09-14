package com.opsagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.opsagent.entity.SysUser;

/**
 * 系统用户的数据访问接口。
 * 继承 BaseMapper 后自动拥有 selectById / selectList / insert / updateById 等方法。
 */
public interface SysUserMapper extends BaseMapper<SysUser> {
}
