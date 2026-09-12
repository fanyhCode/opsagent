# Git 工作流与规范手册

> 适用于个人项目和公司项目。内容包含完整操作流程、公司环境差异、两条安全红线、常见报错排查和速查表。
>
> 维护人：fanyhCode ｜ 最后更新：2026-09-11

## 一、核心概念

| 概念 | 含义 |
| --- | --- |
| 工作区（Working Directory） | 你实际编辑文件的地方，就是项目的文件夹 |
| 暂存区（Staging Area） | `git add` 之后、提交之前的过渡区，相当于"购物车" |
| 本地仓库（Local Repository） | 文件夹里的 `.git` 目录，保存全部提交历史 |
| 远程仓库（Remote） | GitHub / GitLab 上的仓库，习惯命名为 `origin` |
| 提交（Commit） | 一次代码快照，包含改动内容、作者、时间和说明文字 |
| 分支（Branch） | 独立的开发线，默认主线叫 `main` |

数据流向：

```text
工作区 --git add--> 暂存区 --git commit--> 本地仓库 --git push--> 远程仓库
工作区 <--改动-------- 暂存区 <--回退-------- 本地仓库 <--git pull-- 远程仓库
```

一句话记忆：**add 是把改动放上桌，commit 是拍照存档，push 是上传云端。**

## 二、第一次配置（每台电脑只做一次）

```bash
git config --global user.name "你的名字"
git config --global user.email "你的邮箱"
git config --global init.defaultBranch main
```

查看当前配置是否生效：

```bash
git config --list
```

几个常用可选项：

```bash
git config --global core.autocrlf true     # Windows 上建议开启，自动处理换行符
git config --global pull.rebase false      # git pull 时使用默认的合并策略
```

## 三、新项目上传到远程仓库（四步）

### 第一步：在平台上创建空仓库

GitHub 访问 `https://github.com/new`；公司里通常是 GitLab 或自建 Git，入口不同但功能一样。

**不要勾选** Add README、Add .gitignore、Add license。让本地和远程都从同一起点开始，避免一开始就要处理合并冲突。

创建完成后复制仓库地址（HTTPS 或 SSH 形式）。

### 第二步：本地初始化并提交

```bash
cd /d 你的项目目录
git init -b main
git add -A
git commit -m "chore: 初始化项目"
```

### 第三步：关联远程并推送

```bash
git remote add origin 仓库地址
git push -u origin main
```

`-u` 的作用是把本地 `main` 与远程 `origin/main` 绑定，以后直接敲 `git push` 即可。

### 第四步：确认结果

```bash
git remote -v          # 查看远程地址
git log --oneline      # 查看提交历史
```

浏览器刷新仓库页面，能看到文件即上传成功。

## 四、日常开发流程

```bash
git status                    # 1. 看有哪些改动
git add -A                    # 2. 把改动加入暂存区
git commit -m "说明本次改动"   # 3. 提交到本地仓库
git pull                      # 4. 先拉取别人的更新（团队协作必须）
git push                      # 5. 推送到远程
```

工作节奏建议：**功能拆小、频繁提交**。每个可运行的小改动就提交一次，不要攒一星期再提交一次——那样出问题时无法定位，也无法回退。

### 提交说明规范（公司普遍使用）

| 前缀 | 用途 | 示例 |
| --- | --- | --- |
| `feat` | 新增功能 | `feat: 新增 Linux CPU 采集工具` |
| `fix` | 修复缺陷 | `fix: 修复命令白名单校验漏洞` |
| `docs` | 文档改动 | `docs: 补充部署说明` |
| `refactor` | 重构（不改功能） | `refactor: 抽取工具执行器` |
| `chore` | 构建、配置、杂项 | `chore: 初始化项目仓库` |
| `test` | 测试相关 | `test: 补充 Agent Loop 单元测试` |

## 五、团队协作流程（公司里必用）

正规团队不会直接往 `main` 提交，而是走"分支 + 合并请求 + 代码评审"：

```bash
git checkout -b feature/order-service-fix     # 1. 从 main 切出新分支
git add -A
git commit -m "fix: 修复订单服务连接池配置"
git push -u origin feature/order-service-fix  # 2. 推送分支
```

3. 在网页上发起 Merge Request（GitLab）或 Pull Request（GitHub）；
4. 同事做 Code Review，提出修改意见；
5. 评审通过后由负责人合并到 `main`；
6. 本地切回 `main` 并拉取最新代码：

```bash
git checkout main
git pull
```

## 六、公司环境的五个差异

### 1. 平台可能不是 GitHub

GitLab、Gitee、自建 Git 服务都可以，**命令完全一致，只换仓库地址**。`origin` 只是远程仓库的代号。

### 2. 认证方式可能要求 SSH

```bash
ssh-keygen -t ed25519 -C "你的公司邮箱"   # 一路回车，密码可留空
cat ~/.ssh/id_ed25519.pub                  # 复制输出的整行内容
```

把公钥内容粘贴到 GitLab/GitHub 的 **Settings → SSH Keys**，然后改用 SSH 地址：

```bash
git remote set-url origin git@github.com:用户名/仓库.git
ssh -T git@github.com                      # 测试，看到 successfully authenticated 即成功
```

**公钥可以公开，私钥（`id_ed25519`）绝不能外传。**

### 3. 身份需要切换

个人项目用匿名邮箱，公司项目一般要求真实姓名 + 公司邮箱。全局配个人身份，在具体项目里用 `--local` 覆盖：

```bash
cd 公司项目目录
git config --local user.name "张三"
git config --local user.email "zhangsan@company.com"
```

### 4. 网络可能需要代理

```bash
git config --global http.proxy http://代理地址:端口     # 设置
git config --global --unset http.proxy                  # 取消
```

公司内网的 Git 服务器通常不需要代理。

### 5. 权限需要申请

公司仓库一般有访问控制，新同事需要找管理员或项目负责人开通权限，拿到的是"邀请"或"加入项目组"的通知。

## 七、两条安全红线

### 红线一：公司代码不要传到个人仓库

代码归属和保密协议通常写在劳动合同里。工作项目推到公司 Git，个人 GitHub 只放自己的练习项目。

### 红线二：任何密钥都不要进入 Git

数据库密码、云服务密钥、Token 一律写在 `.env` 文件中，并确保 `.gitignore` 里包含：

```text
.env
.env.*
!.env.example
*.pem
*.key
```

如果不小心提交了密钥：**删文件没用，历史记录里还在**。正确做法是立刻去对应平台把密钥作废并重新生成，然后再清理历史。

## 八、常见报错与解决

| 报错信息 | 原因 | 解决办法 |
| --- | --- | --- |
| `detected dubious ownership in repository` | 仓库目录的属主与当前用户不一致 | `git config --global --add safe.directory 仓库绝对路径`，或让仓库由自己创建 |
| `GH007: Your push would publish a private email address` | 提交邮箱是账号里的私密邮箱 | 换成 `用户名@users.noreply.github.com` 后执行 `git commit --amend --reset-author --no-edit` 再推送 |
| `remote: Support for password authentication was removed` | GitHub 不再允许用账号密码推送 | 使用 Git Credential Manager 浏览器登录，或配置 Token / SSH |
| `Permission denied (publickey)` | SSH 公钥没有配置到平台 | 把 `~/.ssh/id_ed25519.pub` 内容添加到平台的 SSH Keys |
| `failed to push some refs ... non-fast-forward` | 远程有别人的新提交 | 先 `git pull --rebase` 解决冲突，再 `git push` |
| `CONFLICT (content): Merge conflict in 文件名` | 两个分支改了同一处代码 | 打开文件手动保留正确内容，删除 `<<<<<<<`、`=======`、`>>>>>>>` 标记后 `git add` 并 `git commit` |
| `fatal: not a git repository` | 当前目录不是 Git 仓库 | 用 `cd` 进入项目目录，或执行 `git init` |
| `LF will be replaced by CRLF` | Windows 换行符转换提示 | 不是错误，可以忽略 |

## 九、速查表

| 场景 | 命令 |
| --- | --- |
| 新项目从零上传 | `git init -b main` → `git add -A` → `git commit` → `git remote add origin 地址` → `git push -u origin main` |
| 拉取已有项目 | `git clone 仓库地址` |
| 日常提交三连 | `git add -A` → `git commit -m "说明"` → `git push` |
| 获取同事的更新 | `git pull` |
| 开发新功能 | `git checkout -b 分支名` → 提交 → `git push -u origin 分支名` → 网页发起 MR/PR |
| 查看状态 | `git status` |
| 查看历史 | `git log --oneline` |
| 查看远程地址 | `git remote -v` |
| 撤销工作区未提交的改动 | `git checkout -- 文件名`（慎用，改动会丢失） |
| 查看某次提交的改动 | `git show 提交号` |

## 十、学习建议

1. 每学一个命令就亲手敲一遍，比看十遍教程有用；
2. 先用小项目把"提交—推送—拉取"练成肌肉记忆，再学分支和冲突处理；
3. 遇到报错先读英文报错本身，Git 大多数报错都会直接告诉你解决命令；
4. 记住一句话：**Git 里的操作几乎都可以回退，但推送出去的密钥不能。**
