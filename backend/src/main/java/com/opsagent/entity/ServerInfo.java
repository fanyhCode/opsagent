package com.opsagent.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 服务器信息实体，对应数据库中的 server 表。
 *
 * 实体类（Entity）的作用：把数据库的一行数据映射成一个 Java 对象。
 * 表结构变化时，这里也要同步调整。
 *
 * @TableName("server") 明确指定对应的表名（数据库里的表叫 server，Java 类叫 ServerInfo，需要手动对应）
 */
@TableName("server")
public class ServerInfo {

    /** 主键，@TableId 标记它是主键，IdType.AUTO 表示使用数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 服务器名称，例如 ubuntu-vm */
    private String name;

    /** 主机地址（IP 或域名） */
    private String host;

    /** SSH 端口，默认 22 */
    private Integer port;

    /** 状态：ONLINE / OFFLINE / UNKNOWN */
    private String status;

    /** 操作系统版本 */
    private String os;

    /** 备注说明 */
    private String description;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getOs() {
        return os;
    }

    public void setOs(String os) {
        this.os = os;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
