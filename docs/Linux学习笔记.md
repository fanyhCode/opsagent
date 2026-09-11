# Linux 学习笔记（边做边记）

按阶段记录做项目时真正用到的 Linux 知识，既是学习台账，也是面试复习材料。

## M0 环境准备

| 命令 / 概念 | 作用 | 面试可讲点 |
| --- | --- | --- |
| `whoami` / `pwd` / `id` | 查看当前用户、目录、用户与所属组 | 理解 Linux 用户与组的权限模型 |
| `lsb_release -a` / `uname -a` | 查看发行版与内核信息 | 排查环境差异、确认依赖兼容性 |
| `free -h` / `df -h` / `nproc` | 查看内存、磁盘、CPU 核心数 | 服务器资源评估，对应项目实施里的容量规划 |
| `ip a` | 查看网卡与 IP 地址 | 理解虚拟机网络、后续 SSH 与访问服务都依赖它 |
| `sudo` | 以管理员权限执行命令 | 权限最小化原则，日常操作不使用 root |
| `sudo apt update` / `apt install` | 更新软件源索引、安装软件 | 软件源与镜像加速，国内网络必须掌握 |
| `curl -fsSL URL \| sudo bash` | 下载脚本并执行 | 风险操作：管道执行远程脚本需确认来源可信 |
| `ps -ef \| grep apt` | 查看进程、过滤关键字 | 判断命令是"卡住"还是"正在运行"的排查方法 |
| `usermod -aG docker $USER` | 将用户加入 docker 组 | 组成员变更需重新登录会话才生效 |
| `/var/run/docker.sock` | Docker 守护进程的 Unix 套接字 | 能访问它 ≈ root 权限，这是命令安全引擎存在的理由 |
| `docker --version` / `docker compose version` | 查看 Docker 与 Compose 版本 | 环境验证的标准动作 |
| `docker run hello-world` | 拉取并运行第一个容器 | 镜像、容器、守护进程三者关系 |

### 踩坑记录

1. **`sudo` 输密码时不显示任何字符**：Linux 的安全设计，盲打后回车即可，不是键盘失灵。
2. **`apt update` 长时间无输出**：安装脚本用 `-qq` 和 `>/dev/null` 静默了输出，用 `ps -ef | grep apt` 确认进程是否在运行。
3. **`permission denied ... docker.sock`**：当前用户不在 docker 组或会话未刷新，`usermod` 后需 `newgrp docker` 或重新登录。
4. **`id docker run hello-world` 报 no such user**：一行 = 一条命令，后面的词都被当作前一个命令的参数。
