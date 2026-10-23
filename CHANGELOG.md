# 更新日志

## 0.1.0

- 登录准入：调用 `control.v1.AuthorizePlayer`，1 秒 deadline，结果按 UUID 缓存。
- 会话遥测：开始 / 心跳 / 转服 / 结束 / 登录失败，经有界队列异步上报。
- 控制面故障：在线玩家不受影响；新登录默认拒绝（`fail-open=false`）。
- 工具链：Gradle 9.8.2 wrapper、Java 17、Spotless + google-java-format、`-Xlint:all -Werror`、JUnit 5。
