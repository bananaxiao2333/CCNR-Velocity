// Copyright (c) 2026 CCNR
// SPDX-License-Identifier: MIT
package com.ccnr.velocity;

import com.ccnr.velocity.ReportEvent.ReportKind;
import com.velocitypowered.api.event.ResultedEvent.ComponentResult;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.event.player.ServerConnectedEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import javax.inject.Inject;
import net.kyori.adventure.text.Component;
import org.slf4j.Logger;

/** 登录准入 + 会话遥测。控制面故障时不阻塞在线玩家。 */
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
      PluginConfig config = loadConfig();
      GrpcControlGateway gateway = new GrpcControlGateway(config);
      this.access = new AccessController(gateway, config.failOpen(), MAX_CACHE_ENTRIES);
      this.telemetry = new TelemetryQueue(gateway, config.queueCapacity(), logger);
      this.telemetry.start();
      this.heartbeat =
          server
              .getScheduler()
              .buildTask(this, this::heartbeatAll)
              .repeat(Duration.ofSeconds(config.heartbeatSeconds()))
              .schedule();
      logger.info(
          "CCNR 控制面已连接：{}（fail-open={}，超时 {} ms）",
          config.endpoint(),
          config.failOpen(),
          config.timeoutMillis());
    } catch (IOException | IllegalArgumentException error) {
      logger.error("CCNR 控制面配置无效，插件停用：{}", error.getMessage());
      shutdown();
    }
  }

  @Subscribe
  public void onLogin(LoginEvent event) {
    if (access == null) {
      return;
    }
    Player player = event.getPlayer();
    String serverId = currentServer(player);
    AccessDecision decision =
        access.authorize(player.getUniqueId().toString(), player.getUsername(), serverId);
    if (decision.allowed()) {
      logger.debug("准入通过 {}@{}", player.getUsername(), serverId);
      return;
    }
    report(
        ReportKind.LOGIN_DENIED,
        "",
        player.getUniqueId().toString(),
        player.getUsername(),
        serverId,
        "",
        decision.reason(),
        "");
    String message = decision.message().isBlank() ? "无法进入服务器" : decision.message();
    event.setResult(ComponentResult.denied(Component.text(message)));
    logger.info("拒绝 {}：{}（{}）", player.getUsername(), decision.reason(), message);
  }

  @Subscribe
  public void onServerConnected(ServerConnectedEvent event) {
    String playerUuid = event.getPlayer().getUniqueId().toString();
    String username = event.getPlayer().getUsername();
    String serverId = event.getServer().getServerInfo().getName();
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
    sessions
        .end(player.getUniqueId().toString())
        .ifPresent(
            session -> {
              logger.info(
                  "会话结束 {}@{}：{}（{}）",
                  player.getUsername(),
                  session.serverId(),
                  event.getLoginStatus().name(),
                  session.sessionId());
              report(
                  ReportKind.SESSION_ENDED,
                  session.sessionId(),
                  player.getUniqueId().toString(),
                  player.getUsername(),
                  session.serverId(),
                  "",
                  "",
                  event.getLoginStatus().name());
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

  private String currentServer(Player player) {
    return player
        .getCurrentServer()
        .map(connection -> connection.getServerInfo().getName())
        .orElse("");
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

  private void shutdown() {
    logger.info("CCNR 控制面插件停止，清理 {} 条在线会话", sessions.size());
    if (heartbeat != null) {
      heartbeat.cancel();
      heartbeat = null;
    }
    if (telemetry != null) {
      telemetry.close();
      telemetry = null;
    }
    access = null;
  }
}
