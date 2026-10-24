// Copyright (c) 2026 CCNR
// SPDX-License-Identifier: MIT
package com.ccnr.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class MessagesTest {

  @Test
  void defaultsAreChinese() {
    Messages messages = Messages.load(new Properties());
    assertEquals("请完成验证以确认登录", messages.text(Messages.LOGIN_HEADING));
    assertEquals("/登录 {code}", messages.text(Messages.LOGIN_COMMAND));
  }

  @Test
  void configOverridesIndividualKeys() {
    Properties properties = new Properties();
    properties.setProperty("message.login.heading", "Please verify to continue");
    properties.setProperty("message.error.access-denied", "You shall not pass");
    Messages messages = Messages.load(properties);
    assertEquals("Please verify to continue", messages.text(Messages.LOGIN_HEADING));
    assertEquals("You shall not pass", messages.text(Messages.ERROR_ACCESS_DENIED));
    assertEquals("✔ 验证通过，正在进入游戏服…", messages.text(Messages.LOGIN_SUCCESS));
  }

  @Test
  void blankOverrideFallsBackToDefault() {
    Properties properties = new Properties();
    properties.setProperty("message.login.title", "   ");
    Messages messages = Messages.load(properties);
    assertEquals("━━━━━━━━ CCNR 通行证 ━━━━━━━━", messages.text(Messages.LOGIN_TITLE));
  }

  @Test
  void formatReplacesPlaceholders() {
    Messages messages = Messages.load(new Properties());
    assertEquals(
        "/登录 ABCD-1234", messages.format(Messages.LOGIN_COMMAND, Map.of("code", "ABCD-1234")));
    assertEquals(
        "完成后将会自动通过验证（5 分钟内有效）", messages.format(Messages.LOGIN_FOOTER, Map.of("minutes", "5")));
  }

  @Test
  void formatKeepsUnknownPlaceholders() {
    Messages messages = Messages.load(new Properties());
    assertEquals("/登录 {code}", messages.format(Messages.LOGIN_COMMAND, Map.of("other", "x")));
  }

  @Test
  void unknownKeyIsRejected() {
    Messages messages = Messages.load(new Properties());
    assertThrows(IllegalArgumentException.class, () -> messages.text("login.missing"));
  }

  @Test
  void configCanTranslateWholePrompt() {
    Properties properties = new Properties();
    properties.setProperty("message.login.title", "==== CCNR Pass ====");
    properties.setProperty("message.login.heading", "Verify to sign in");
    properties.setProperty("message.login.button", "[ Sign in ]");
    properties.setProperty("message.login.or-command", "or send this to the CCNR bot:");
    properties.setProperty("message.login.footer", "Valid for {minutes} minutes");
    Messages messages = Messages.load(properties);
    assertTrue(messages.text(Messages.LOGIN_TITLE).startsWith("===="));
    assertEquals("Verify to sign in", messages.text(Messages.LOGIN_HEADING));
    assertEquals(
        "Valid for 2 minutes", messages.format(Messages.LOGIN_FOOTER, Map.of("minutes", "2")));
  }
}
