// Copyright (c) 2026 CCNR
// SPDX-License-Identifier: MIT
package com.ccnr.velocity;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * 玩家可见文案：内置中文默认值，可在 {@code config.properties} 里用 {@code message.*} 覆盖。
 *
 * <p>缺省、空值或删掉的键都回落到内置默认值，因此老配置文件升级后不会因为少键启动失败。 占位符用 {@code {name}} 形式，由 {@link #format(String,
 * Map)} 替换。
 */
public final class Messages {

  static final String LOGIN_TITLE = "login.title";
  static final String LOGIN_HEADING = "login.heading";
  static final String LOGIN_BUTTON = "login.button";
  static final String LOGIN_OR_COMMAND = "login.or-command";
  static final String LOGIN_COMMAND = "login.command";
  static final String LOGIN_FOOTER = "login.footer";
  static final String LOGIN_ACTIONBAR = "login.actionbar";
  static final String LOGIN_ACTIONBAR_HINT = "login.actionbar-hint";
  static final String LOGIN_SUCCESS = "login.success";
  static final String LOGIN_HOVER_COMMAND = "login.hover-command";
  static final String ERROR_NOT_VERIFIED = "error.not-verified";
  static final String ERROR_PASS_UNAVAILABLE = "error.pass-unavailable";
  static final String ERROR_LOBBY_UNAVAILABLE = "error.lobby-unavailable";
  static final String ERROR_ACCESS_DENIED = "error.access-denied";
  static final String ERROR_LOGIN_FAILED = "error.login-failed";

  private static final String PREFIX = "message.";

  private static final Map<String, String> DEFAULTS = defaults();

  private final Properties overrides;

  private Messages(Properties overrides) {
    this.overrides = overrides;
  }

  static Messages load(Properties config) {
    return new Messages(config);
  }

  /** 取原始模板；配置里没有就回落到内置默认值。 */
  String text(String key) {
    String override = overrides.getProperty(PREFIX + key, "").trim();
    if (!override.isEmpty()) {
      return override;
    }
    String fallback = DEFAULTS.get(key);
    if (fallback == null) {
      throw new IllegalArgumentException("未知的文案键：" + key);
    }
    return fallback;
  }

  /** 替换 {@code {name}} 占位符后返回最终文案。 */
  String format(String key, Map<String, String> variables) {
    String result = text(key);
    for (Map.Entry<String, String> variable : variables.entrySet()) {
      result = result.replace("{" + variable.getKey() + "}", variable.getValue());
    }
    return result;
  }

  private static Map<String, String> defaults() {
    Map<String, String> defaults = new LinkedHashMap<>();
    defaults.put(LOGIN_TITLE, "━━━━━━━━ CCNR 通行证 ━━━━━━━━");
    defaults.put(LOGIN_HEADING, "请完成验证以确认登录");
    defaults.put(LOGIN_BUTTON, "[ 点击登录 ]");
    defaults.put(LOGIN_OR_COMMAND, "或者向 CCNR 行政服务发送下面的命令");
    defaults.put(LOGIN_COMMAND, "/登录 {code}");
    defaults.put(LOGIN_FOOTER, "完成后将会自动通过验证（{minutes} 分钟内有效）");
    defaults.put(LOGIN_ACTIONBAR, "[点击登录]");
    defaults.put(LOGIN_ACTIONBAR_HINT, "或 /登录 {code}");
    defaults.put(LOGIN_SUCCESS, "✔ 验证通过，正在进入游戏服…");
    defaults.put(LOGIN_HOVER_COMMAND, "点击复制命令");
    defaults.put(ERROR_NOT_VERIFIED, "请先完成 CCNR Pass 登录后再进入游戏服务器");
    defaults.put(ERROR_PASS_UNAVAILABLE, "CCNR Pass 暂时不可用，请稍后再试");
    defaults.put(ERROR_LOBBY_UNAVAILABLE, "游戏服暂不可用，请联系管理员");
    defaults.put(ERROR_ACCESS_DENIED, "你无法进入服务器");
    defaults.put(ERROR_LOGIN_FAILED, "CCNR Pass 登录失败或已过期");
    return Map.copyOf(defaults);
  }
}
