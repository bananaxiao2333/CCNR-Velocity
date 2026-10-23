// Copyright (c) 2026 CCNR
// SPDX-License-Identifier: MIT
package com.ccnr.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AccessControllerTest {

  private static final class FakeGateway implements ControlGateway {
    private final AtomicInteger calls = new AtomicInteger();
    private boolean fail;
    private AccessDecision decision = AccessDecision.allow(60);

    @Override
    public AccessDecision authorize(String playerUuid, String username, String serverId)
        throws ControlUnavailableException {
      calls.incrementAndGet();
      if (fail) {
        throw new ControlUnavailableException("down", new RuntimeException("down"));
      }
      return decision;
    }

    @Override
    public void report(ReportEvent event) {}
  }

  @Test
  void cachesAllowedDecision() {
    FakeGateway gateway = new FakeGateway();
    AccessController controller = new AccessController(gateway, false, 16);
    assertTrue(controller.authorize("uuid", "Alice", "lobby").allowed());
    assertTrue(controller.authorize("uuid", "Alice", "lobby").allowed());
    assertEquals(1, gateway.calls.get());
  }

  @Test
  void deniesWhenControlPlaneDownAndFailOpenFalse() {
    FakeGateway gateway = new FakeGateway();
    gateway.fail = true;
    AccessController controller = new AccessController(gateway, false, 16);
    AccessDecision decision = controller.authorize("uuid", "Alice", "lobby");
    assertFalse(decision.allowed());
    assertEquals("CASDOOR_UNAVAILABLE", decision.reason());
  }

  @Test
  void allowsWhenControlPlaneDownAndFailOpenTrue() {
    FakeGateway gateway = new FakeGateway();
    gateway.fail = true;
    AccessController controller = new AccessController(gateway, true, 16);
    assertTrue(controller.authorize("uuid", "Alice", "lobby").allowed());
  }

  @Test
  void denyIsNotCached() {
    FakeGateway gateway = new FakeGateway();
    gateway.decision = AccessDecision.deny("BANNED", "no");
    AccessController controller = new AccessController(gateway, false, 16);
    assertFalse(controller.authorize("uuid", "Alice", "lobby").allowed());
    assertFalse(controller.authorize("uuid", "Alice", "lobby").allowed());
    assertEquals(2, gateway.calls.get());
  }
}
