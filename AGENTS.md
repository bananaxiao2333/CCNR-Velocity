# AGENTS.md · 在本仓库工作的硬性约定

规范全文见 [docs/01-工程规范](docs/01-工程规范.md)。这里只列最容易踩的。

## 命令

```bash
./scripts/check.sh                 # 提交前必须全绿
./gradlew test                     # 快速迭代
./scripts/sync-proto.sh            # CCNR-Data 改了 control.proto 之后
```

需要 **JDK 17+**（JRE 不行），Gradle 用仓库内 wrapper，不要另装。

## 提交前必须过

`scripts/check.sh`：spotlessCheck、`-Werror` 编译、JUnit、shadowJar、proto 一致性。
CI 跑同一份脚本。

## 禁止

| 禁止 | 说明 |
|---|---|
| 占位代码 / 假实现 | 不写空方法、不返回假数据、不留"以后再实现" |
| 未使用的字段 / 配置项 | 配置项必须有读取点与文档 |
| 阻塞游戏线程 | 所有网络调用必须带 deadline；禁无限等待、禁重试风暴 |
| 无界队列 | 遥测队列必须有容量上限，满了丢新事件并告警 |
| `System.out` / `printStackTrace` | 一律走 SLF4J |
| 手改生成的 gRPC 桩 | 生成物在 build 目录，不提交 |
| 直接改 `control.proto` 副本 | 先在 CCNR-Data 改，再 `scripts/sync-proto.sh` |

## 改动时

- **新增 RPC 调用**：先看 CCNR-Data 的 `docs/02` 与 `control.proto`；同步契约后再写客户端。
- **新增事件**：同时更新 `ReportEvent.ReportKind`、proto 映射、`docs/02` 的事件表与单测。
- **改配置**：`PluginConfig` 增加校验与范围、`config.properties` 增加注释、README 配置表同步。
- **日志**：连接/转服/结束用 INFO，准入通过用 DEBUG；拒绝与故障用 INFO/WARN；**绝不打印 token**。
- **测试**：只允许在 `ControlGateway` 这一层用假实现；配置、缓存、会话、队列逻辑走真实代码。

## 与 CCNR-Data 的关系

本仓库是 Velocity 侧客户端，**不实现业务规则**：封禁、白名单、角色 ACL、身份绑定全在 CCNR-Data。
这里只做三件事：问一次、缓存一会儿、把发生的事报回去。
