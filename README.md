# CCNR-Velocity

Velocity 代理侧的 **准入与遥测** 插件：登录时问 CCNR-Data「能不能进」，在线时把会话事件报回去。
Java 17 · Velocity 3.4 · gRPC `control.v1`。

```
玩家 ──► Velocity ──► ccnr-control ──gRPC──► CCNR-Data（control.v1）
                          │  LoginEvent：同步决策（1s deadline）
                          │  Session：开始 / 心跳 / 转服 / 结束 / 登录失败
                          └─ 有界队列异步上报，绝不阻塞游戏线程
```

## 行为

| 时机 | 动作 |
|---|---|
| `LoginEvent` | 调 `AuthorizePlayer`；拒绝时给玩家可读原因。结果按 UUID 缓存 TTL 秒 |
| `ServerConnectedEvent` | 首次连接 → `SESSION_STARTED`；换服 → `SERVER_SWITCHED` |
| 定时（默认 60s） | 对每个在线会话发 `SESSION_HEARTBEAT`（服务端只刷新 `last_seen`） |
| `DisconnectEvent` | `SESSION_ENDED`，原因取 Velocity 的 `LoginStatus` |
| 登录被拒 | `LOGIN_DENIED` + 拒绝原因（供统计） |
| 控制面不可达 | 在线玩家不受影响；新登录按 `fail-open`（默认 `false` = 拒绝） |

## 构建与门禁

只要求 **JDK 17+**（必须是 JDK，不能是 JRE）与网络；Gradle 由 wrapper 固定为 9.8.2。

```bash
./scripts/check.sh          # spotlessCheck + 编译 + 单测 + shadowJar
./gradlew build             # 等价入口；产物在 build/libs/
```

门禁内容：

1. `spotlessCheck`：google-java-format + SPDX 许可头；
2. `compileJava`：`-Xlint:all -Werror`，警告即失败；
3. `test`：JUnit 5，覆盖配置校验、缓存、准入兜底、会话状态、遥测队列；
4. `shadowJar`：把 gRPC / protobuf 重定位后打进单个 jar；
5. `scripts/sync-proto.sh` 的一致性检查（有同级 CCNR-Data 时）。

插件自带上游契约副本 `src/main/proto/control/v1/control.proto`，因此可以独立构建。
改契约的顺序永远是：**CCNR-Data 改 proto → 生成桩 → 本仓库 `scripts/sync-proto.sh` → 重新构建**。

## 安装

1. 把 `build/libs/ccnr-control-velocity-0.1.0.jar` 放进 Velocity 的 `plugins/`。
2. 启动一次，生成 `plugins/ccnr-control/config.properties`。
3. 填 `endpoint` / `token`，重启。

## 配置

| 键 | 默认 | 说明 |
|---|---|---|
| `endpoint` | 必填 | CCNR-Data gRPC 地址，`host:port` |
| `token` | 空 | `control.v1` Bearer token；服务端未开鉴权时留空 |
| `fail-open` | `false` | 控制面故障时是否放行新登录；生产保持 `false` |
| `timeout-millis` | `1000` | 单次 gRPC 超时，100–10000 |
| `cache-ttl-seconds` | `60` | 本地准入缓存；服务端返回的 `cache_ttl_seconds` 优先 |
| `heartbeat-seconds` | `60` | 心跳间隔，10–600 |
| `queue-capacity` | `8192` | 遥测队列上限，满了丢新事件并告警 |
| `proxy-id` | `velocity` | 代理实例标识，落 `proxy_sessions.proxy_id` |

越界或半配置 **启动失败并写 ERROR 日志**，不会静默钳制。

## 目录

```
src/main/java/com/ccnr/velocity/   插件与纯逻辑（准入、缓存、会话、队列、gRPC 客户端）
src/main/proto/control/v1/         control.v1 契约副本（从 CCNR-Data 同步）
src/main/resources/                默认 config.properties
src/test/java/                     JUnit 5 单测
docs/                              设计与运维文档
docs/adr/                          架构决策记录
scripts/                           check.sh / sync-proto.sh
```

## 文档

| 文档 | 内容 |
|---|---|
| [docs/00-总览](docs/00-总览.md) | 定位、边界、失败模式 |
| [docs/01-工程规范](docs/01-工程规范.md) | 代码、日志、测试、门禁规则 |
| [docs/02-协议与事件](docs/02-协议与事件.md) | 每个事件的语义与幂等键 |
| [docs/03-部署与运维](docs/03-部署与运维.md) | 安装、排障、日志 |
| [docs/adr](docs/adr/README.md) | 关键取舍 |

## 许可

MIT，见 [LICENSE](LICENSE)。
