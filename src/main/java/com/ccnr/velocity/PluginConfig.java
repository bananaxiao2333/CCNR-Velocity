// Copyright (c) 2026 CCNR
// SPDX-License-Identifier: MIT
package com.ccnr.velocity;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.regex.Pattern;

/** 插件配置：properties 文件，缺项/越界一律启动失败，不静默钳制。 */
public record PluginConfig(
    String endpoint,
    String token,
    boolean failOpen,
    int timeoutMillis,
    int authorizeTimeoutMillis,
    int cacheTtlSeconds,
    int heartbeatSeconds,
    int queueCapacity,
    String proxyId,
    String authServer,
    String lobbyServer,
    Messages messages) {

  private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_.:-]*");

  public static PluginConfig load(Path file) throws IOException {
    Properties properties = new Properties();
    try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
      properties.load(reader);
    }
    String endpoint = required(properties, "endpoint");
    String token = properties.getProperty("token", "").trim();
    boolean failOpen = bool(properties, "fail-open", false);
    int timeout = integer(properties, "timeout-millis", 1000, 100, 10_000);
    int authorizeTimeout = integer(properties, "authorize-timeout-millis", 3000, 500, 15_000);
    int cacheTtl = integer(properties, "cache-ttl-seconds", 60, 0, 3600);
    int heartbeat = integer(properties, "heartbeat-seconds", 60, 10, 600);
    int queue = integer(properties, "queue-capacity", 8192, 128, 100_000);
    String proxyId = properties.getProperty("proxy-id", "velocity").trim();
    if (!IDENTIFIER.matcher(proxyId).matches()) {
      throw new IllegalArgumentException("proxy-id 只允许字母、数字与 _ . : -：" + proxyId);
    }
    String authServer = identifier(properties, "auth-server", "auth");
    String lobbyServer = identifier(properties, "lobby-server", "lobby");
    if (authServer.equals(lobbyServer)) {
      throw new IllegalArgumentException("auth-server 与 lobby-server 不能相同：" + authServer);
    }
    return new PluginConfig(
        endpoint,
        token,
        failOpen,
        timeout,
        authorizeTimeout,
        cacheTtl,
        heartbeat,
        queue,
        proxyId,
        authServer,
        lobbyServer,
        Messages.load(properties));
  }

  private static String identifier(Properties properties, String key, String fallback) {
    String value = properties.getProperty(key, fallback).trim();
    if (!IDENTIFIER.matcher(value).matches()) {
      throw new IllegalArgumentException(key + " 只允许字母、数字与 _ . : -：" + value);
    }
    return value;
  }

  private static String required(Properties properties, String key) {
    String value = properties.getProperty(key, "").trim();
    if (value.isEmpty()) {
      throw new IllegalArgumentException("缺少必填配置：" + key);
    }
    return value;
  }

  private static boolean bool(Properties properties, String key, boolean fallback) {
    String raw = properties.getProperty(key, "").trim();
    if (raw.isEmpty()) {
      return fallback;
    }
    if ("true".equalsIgnoreCase(raw)) {
      return true;
    }
    if ("false".equalsIgnoreCase(raw)) {
      return false;
    }
    throw new IllegalArgumentException(key + " 必须是 true / false：" + raw);
  }

  private static int integer(Properties properties, String key, int fallback, int low, int high) {
    String raw = properties.getProperty(key, "").trim();
    int value = fallback;
    if (!raw.isEmpty()) {
      try {
        value = Integer.parseInt(raw);
      } catch (NumberFormatException error) {
        throw new IllegalArgumentException(key + " 不是整数：" + raw, error);
      }
    }
    if (value < low || value > high) {
      throw new IllegalArgumentException(key + " 必须在 " + low + "-" + high + " 之间：" + value);
    }
    return value;
  }
}
