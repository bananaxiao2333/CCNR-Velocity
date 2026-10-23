# ADR-0002 · 登录改为 CCNR Pass 设备码 + limbo

- 状态：接受
- 日期：2026-10-24
- 决策者：CCNR

## 背景

ADR-0001 让 `LoginEvent` 同步调 `AuthorizePlayer`。它能判断"这个 UUID 有没有绑定、封没封"，
但无法证明"这次连接就是账号本人"：代理是 offline-mode，改个名字就能复用别人的绑定。
CCNR Skin 的 Yggdrasil 又要本地密码，OAuth 账号没有密码，走不通。

## 决策

1. 所有登录先进 `auth`（limbo）；插件调 `BeginDeviceLogin` 拿 `user_code`；
2. 玩家在 CCNR Pass 浏览器授权；服务端轮询 device token 并写身份投影；
3. 插件只认**这条连接**的 APPROVED 结果，之后才调 `AuthorizePlayer` 并送 `lobby`；
4. 已在线玩家不受控制面故障影响；新登录失败就断开，不缓存跨连接授权。

## 后果

- 好处：每条连接都有 Pass 授权证明；不再依赖 Skin 密码。
- 代价：需要一个 limbo 服（NanoLimbo）；登录多一次浏览器操作。
- 重新评估：如果 Casdoor 支持更轻的 OAuth 回传，可替换设备码。
