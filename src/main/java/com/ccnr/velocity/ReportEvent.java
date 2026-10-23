// Copyright (c) 2026 CCNR
// SPDX-License-Identifier: MIT
package com.ccnr.velocity;

import java.time.Instant;

/** 与 control.v1 解耦的遥测事件；由 gRPC 客户端翻译成 proto。 */
public record ReportEvent(
    String requestId,
    ReportKind kind,
    String sessionId,
    String playerUuid,
    String username,
    String serverId,
    String fromServerId,
    String denyReason,
    String endReason,
    Instant occurredAt) {

  public enum ReportKind {
    SESSION_STARTED,
    SESSION_HEARTBEAT,
    SERVER_SWITCHED,
    SESSION_ENDED,
    LOGIN_DENIED
  }
}
