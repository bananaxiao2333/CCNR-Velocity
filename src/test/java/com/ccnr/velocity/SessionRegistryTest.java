// Copyright (c) 2026 CCNR
// SPDX-License-Identifier: MIT
package com.ccnr.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class SessionRegistryTest {

  @Test
  void startIsIdempotentPerPlayer() {
    SessionRegistry registry = new SessionRegistry();
    Optional<SessionRegistry.Session> first = registry.start("uuid", "lobby");
    assertTrue(first.isPresent());
    assertTrue(registry.start("uuid", "lobby").isEmpty());
    assertEquals("lobby", registry.current("uuid").orElseThrow().serverId());
  }

  @Test
  void switchKeepsSessionIdAndReportsOldServer() {
    SessionRegistry registry = new SessionRegistry();
    String sessionId = registry.start("uuid", "lobby").orElseThrow().sessionId();
    Optional<SessionRegistry.Switched> switched = registry.switchServer("uuid", "survival");
    assertTrue(switched.isPresent());
    assertEquals(sessionId, switched.orElseThrow().session().sessionId());
    assertEquals("lobby", switched.orElseThrow().fromServerId());
    assertTrue(registry.switchServer("uuid", "survival").isEmpty());
  }

  @Test
  void endRemovesSession() {
    SessionRegistry registry = new SessionRegistry();
    registry.start("uuid", "lobby");
    assertTrue(registry.end("uuid").isPresent());
    assertFalse(registry.current("uuid").isPresent());
    assertTrue(registry.end("uuid").isEmpty());
  }
}
