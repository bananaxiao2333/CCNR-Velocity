// Copyright (c) 2026 CCNR
// SPDX-License-Identifier: MIT
package com.ccnr.velocity;

import com.ccnr.velocity.ReportEvent.ReportKind;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Inject;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.slf4j.Logger;

/**
 * CCNR Pass 设备码登录 + 会话遥测。
 *
 * <p>玩家先连 auth 服，完成 Pass 授权后才被送到 lobby；授权与这条连接绑定， 不依赖名字/重连，也不会出现"换个客户端用同一个名字就进来"。
 */
@Plugin(
    id = "ccnr-control",
    name = "CCNR Control",
    version = "0.1.0",
    description = "CCNR 统一准入与统计",
    authors = {"CCNR"})
public final class ControlPlugin {

  private static final int MAX_CACHE_ENTRIES = 8192;

  private final ProxyServer server;
  private final Logger logger;
  private final Path dataDirectory;
  private final SessionRegistry sessions = new SessionRegistry();
  private final Set<String> verified = ConcurrentHashMap.newKeySet();
  private final Map<String, PendingLogin> pending = new ConcurrentHashMap<>();

  private PluginConfig config;
  private ControlGateway gateway;
  private AccessController access;
  private TelemetryQueue telemetry;
  private ScheduledTask heartbeat;

  @Inject
  public ControlPlugin(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
    this.server = server;
    this.logger = logger;
    this.dataDirectory = dataDirectory;
  }

  @Subscribe
  public void onProxyInitialize(ProxyInitializeEvent event) {
    try {
      config = loadConfig();
      gateway = new GrpcControlGateway(config);
      access = new AccessController(gateway, config.failOpen(), MAX_CACHE_ENTRIES);
      telemetry = new TelemetryQueue(gateway, config.queueCapacity(), logger);
      telemetry.start();
      heartbeat =
          server
              .getScheduler()
              .buildTask(this, this::heartbeatAll)
              .repeat(Duration.ofSeconds(config.heartbeatSeconds()))
              .schedule();
      logger.info(
          "CCNR 控制面已连接：{}（auth={}，lobby={}，fail-open={}）",
          config.endpoint(),
          config.authServer(),
          config.lobbyServer(),
          config.failOpen());
    } catch (IOException | IllegalArgumentException error) {
      logger.error("CCNR 控制面配置无效，插件停用：{}", error.getMessage());
      shutdown();
    }
  }

  @Subscribe
  public void onServerConnected(ServerConnectedEvent event) {
    if (config == null || gateway == null) {
      return;
    }
    Player player = event.getPlayer();
    String serverId = event.getServer().getServerInfo().getName();
    if (serverId.equals(config.authServer())) {
      holdInAuth(player);
      return;
    }
    if (!verified.contains(player.getUniqueId().toString())) {
      logger.warn("{} 未经 Pass 验证就尝试进入 {}，断开", player.getUsername(), serverId);
      player.disconnect(Component.text(config.messages().text(Messages.ERROR_NOT_VERIFIED)));
      return;
    }
    startOrSwitchSession(player, serverId);
  }

  private void holdInAuth(Player player) {
    String playerUuid = player.getUniqueId().toString();
    if (verified.contains(playerUuid)) {
      sendToLobby(player);
      return;
    }
    if (pending.containsKey(playerUuid)) {
      return;
    }
    try {
      DeviceLoginInfo info =
          gateway.beginDeviceLogin(playerUuid, player.getUsername(), config.proxyId());
      PendingLogin state = new PendingLogin(info);
      pending.put(playerUuid, state);
      sendLoginPrompt(player, info);
      state.actionBar =
          server
              .getScheduler()
              .buildTask(this, () -> sendActionBar(player, info))
              .repeat(Duration.ofSeconds(2))
              .schedule();
      state.task =
          server
              .getScheduler()
              .buildTask(this, () -> pollDeviceLogin(player, state))
              .repeat(Duration.ofSeconds(Math.max(1, info.interval())))
              .schedule();
      logger.info(
          "等待 {} 完成 CCNR Pass 登录（{}，{} 秒内有效）",
          player.getUsername(),
          info.userCode(),
          info.expiresIn());
    } catch (ControlUnavailableException error) {
      logger.warn("无法发起 CCNR Pass 登录：{}", error.getMessage());
      player.disconnect(Component.text(config.messages().text(Messages.ERROR_PASS_UNAVAILABLE)));
    }
  }

  private void pollDeviceLogin(Player player, PendingLogin state) {
    if (!player.isActive() || pending.get(player.getUniqueId().toString()) != state) {
      cancel(state);
      return;
    }
    DeviceLoginState status;
    try {
      status = gateway.deviceLoginStatus(state.info.loginId());
    } catch (ControlUnavailableException error) {
      logger.warn("查询 CCNR Pass 登录状态失败：{}", error.getMessage());
      return;
    }
    switch (status.state()) {
      case PENDING -> {
        return;
      }
      case APPROVED -> finishLogin(player, state, status);
      default -> {
        cancel(state);
        pending.remove(player.getUniqueId().toString());
        String message =
            status.message().isBlank()
                ? config.messages().text(Messages.ERROR_LOGIN_FAILED)
                : status.message();
        logger.info("{} CCNR Pass 登录未通过：{}", player.getUsername(), message);
        player.disconnect(Component.text(message));
      }
    }
  }

  private void finishLogin(Player player, PendingLogin state, DeviceLoginState status) {
    pending.remove(player.getUniqueId().toString());
    AccessDecision decision =
        access.authorize(
            player.getUniqueId().toString(), player.getUsername(), config.lobbyServer());
    if (!decision.allowed()) {
      logger.info("{} 通过 Pass 登录但准入被拒：{}", player.getUsername(), decision.reason());
      player.disconnect(
          Component.text(
              decision.message().isBlank()
                  ? config.messages().text(Messages.ERROR_ACCESS_DENIED)
                  : decision.message()));
      return;
    }
    verified.add(player.getUniqueId().toString());
    logger.info("CCNR Pass 登录通过：{}（{}）", player.getUsername(), status.casdoorUserId());
    player.sendMessage(
        Component.text(
            config.messages().text(Messages.LOGIN_SUCCESS),
            NamedTextColor.GREEN,
            TextDecoration.BOLD));
    sendToLobby(player);
    // 最后再取消自己：先 cancel 会中断当前线程，把 AuthorizePlayer 变成 CANCELLED。
    cancel(state);
  }

  private void sendLoginPrompt(Player player, DeviceLoginInfo info) {
    // 按钮独占一行，方便手机端直接点；下方给出机器人命令作为备用入口。文案全部走 Messages。
    Messages messages = config.messages();
    String code = info.userCode();
    String minutes = String.valueOf(Math.max(1, info.expiresIn() / 60));
    player.sendMessage(Component.text(messages.text(Messages.LOGIN_TITLE), NamedTextColor.GOLD));
    player.sendMessage(
        Component.text(
            messages.text(Messages.LOGIN_HEADING), NamedTextColor.YELLOW, TextDecoration.BOLD));
    player.sendMessage(
        Component.text(
                messages.text(Messages.LOGIN_BUTTON),
                NamedTextColor.GREEN,
                TextDecoration.BOLD,
                TextDecoration.UNDERLINED)
            .clickEvent(ClickEvent.openUrl(info.verificationUri()))
            .hoverEvent(
                HoverEvent.showText(Component.text(info.verificationUri(), NamedTextColor.GRAY))));
    player.sendMessage(
        Component.text(messages.text(Messages.LOGIN_OR_COMMAND), NamedTextColor.GRAY));
    player.sendMessage(
        Component.text(
                messages.format(Messages.LOGIN_COMMAND, Map.of("code", code)),
                NamedTextColor.AQUA,
                TextDecoration.BOLD)
            .clickEvent(ClickEvent.copyToClipboard("/登录 " + code))
            .hoverEvent(
                HoverEvent.showText(
                    Component.text(
                        messages.text(Messages.LOGIN_HOVER_COMMAND), NamedTextColor.GRAY))));
    player.sendMessage(
        Component.text(
            messages.format(Messages.LOGIN_FOOTER, Map.of("minutes", minutes)),
            NamedTextColor.DARK_GRAY));
  }

  private void sendActionBar(Player player, DeviceLoginInfo info) {
    if (!player.isActive()) {
      return;
    }
    Messages messages = config.messages();
    player.sendActionBar(
        Component.text()
            .append(
                Component.text(
                        messages.text(Messages.LOGIN_ACTIONBAR),
                        NamedTextColor.YELLOW,
                        TextDecoration.UNDERLINED)
                    .clickEvent(ClickEvent.openUrl(info.verificationUri())))
            .append(Component.text(" ", NamedTextColor.GRAY))
            .append(
                Component.text(
                        messages.format(
                            Messages.LOGIN_ACTIONBAR_HINT, Map.of("code", info.userCode())),
                        NamedTextColor.GRAY)
                    .clickEvent(ClickEvent.copyToClipboard("/登录 " + info.userCode())))
            .build());
  }

  private void sendToLobby(Player player) {
    Optional<RegisteredServer> lobby = server.getServer(config.lobbyServer());
    if (lobby.isEmpty()) {
      logger.error("velocity.toml 里没有 lobby 服：{}", config.lobbyServer());
      player.disconnect(Component.text(config.messages().text(Messages.ERROR_LOBBY_UNAVAILABLE)));
      return;
    }
    player.createConnectionRequest(lobby.get()).fireAndForget();
  }

  private void startOrSwitchSession(Player player, String serverId) {
    String playerUuid = player.getUniqueId().toString();
    String username = player.getUsername();
    Optional<SessionRegistry.Session> current = sessions.current(playerUuid);
    if (current.isEmpty()) {
      sessions
          .start(playerUuid, serverId)
          .ifPresent(
              session -> {
                logger.info("会话开始 {}@{}（{}）", username, serverId, session.sessionId());
                report(
                    ReportKind.SESSION_STARTED,
                    session.sessionId(),
                    playerUuid,
                    username,
                    serverId,
                    "",
                    "",
                    "");
              });
      return;
    }
    sessions
        .switchServer(playerUuid, serverId)
        .ifPresent(
            switched -> {
              logger.info(
                  "转服 {}：{} → {}（{}）",
                  username,
                  switched.fromServerId(),
                  serverId,
                  switched.session().sessionId());
              report(
                  ReportKind.SERVER_SWITCHED,
                  switched.session().sessionId(),
                  playerUuid,
                  username,
                  serverId,
                  switched.fromServerId(),
                  "",
                  "");
            });
  }

  @Subscribe
  public void onDisconnect(DisconnectEvent event) {
    Player player = event.getPlayer();
    String playerUuid = player.getUniqueId().toString();
    PendingLogin state = pending.remove(playerUuid);
    if (state != null) {
      cancel(state);
    }
    verified.remove(playerUuid);
    // SUCCESSFUL_LOGIN 是"正常登录后离开"，对会话语义就是 quit。
    String endReason =
        event.getLoginStatus() == DisconnectEvent.LoginStatus.SUCCESSFUL_LOGIN
            ? "quit"
            : event.getLoginStatus().name().toLowerCase(Locale.ROOT);
    sessions
        .end(playerUuid)
        .ifPresent(
            session -> {
              logger.info(
                  "会话结束 {}@{}：{}（{}）",
                  player.getUsername(),
                  session.serverId(),
                  endReason,
                  session.sessionId());
              report(
                  ReportKind.SESSION_ENDED,
                  session.sessionId(),
                  playerUuid,
                  player.getUsername(),
                  session.serverId(),
                  "",
                  "",
                  endReason);
            });
  }

  @Subscribe
  public void onProxyShutdown(ProxyShutdownEvent event) {
    shutdown();
  }

  private void heartbeatAll() {
    for (var entry : sessions.snapshot()) {
      SessionRegistry.Session session = entry.getValue();
      report(
          ReportKind.SESSION_HEARTBEAT,
          session.sessionId(),
          entry.getKey(),
          "",
          session.serverId(),
          "",
          "",
          "");
    }
  }

  private void report(
      ReportKind kind,
      String sessionId,
      String playerUuid,
      String username,
      String serverId,
      String fromServerId,
      String denyReason,
      String endReason) {
    if (telemetry == null) {
      return;
    }
    telemetry.submit(
        new ReportEvent(
            UUID.randomUUID().toString(),
            kind,
            sessionId,
            playerUuid,
            username,
            serverId,
            fromServerId,
            denyReason,
            endReason,
            Instant.now()));
  }

  private PluginConfig loadConfig() throws IOException {
    Files.createDirectories(dataDirectory);
    Path configFile = dataDirectory.resolve("config.properties");
    if (Files.notExists(configFile)) {
      try (InputStream defaults = getClass().getResourceAsStream("/config.properties")) {
        if (defaults == null) {
          throw new IOException("缺少内置 config.properties");
        }
        Files.copy(defaults, configFile);
      }
    }
    return PluginConfig.load(configFile);
  }

  private static void cancel(PendingLogin state) {
    ScheduledTask task = state.task;
    if (task != null) {
      task.cancel();
    }
    ScheduledTask actionBar = state.actionBar;
    if (actionBar != null) {
      actionBar.cancel();
    }
  }

  private void shutdown() {
    logger.info("CCNR 控制面插件停止，清理 {} 条在线会话", sessions.size());
    pending.values().forEach(ControlPlugin::cancel);
    pending.clear();
    if (heartbeat != null) {
      heartbeat.cancel();
      heartbeat = null;
    }
    if (telemetry != null) {
      telemetry.close();
      telemetry = null;
    }
    if (gateway != null) {
      gateway.close();
      gateway = null;
    }
    access = null;
  }

  private static final class PendingLogin {
    private final DeviceLoginInfo info;
    private volatile ScheduledTask task;
    private volatile ScheduledTask actionBar;

    private PendingLogin(DeviceLoginInfo info) {
      this.info = info;
    }
  }
}
