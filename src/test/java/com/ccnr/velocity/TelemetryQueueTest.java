// Copyright (c) 2026 CCNR
// SPDX-License-Identifier: MIT
package com.ccnr.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class TelemetryQueueTest {

  private static final class RecordingGateway implements ControlGateway {
    private final List<ReportEvent> events = new CopyOnWriteArrayList<>();
    private final CountDownLatch latch = new CountDownLatch(1);

    @Override
    public AccessDecision authorize(String playerUuid, String username, String serverId) {
      return AccessDecision.allow(60);
    }

    @Override
    public void report(ReportEvent event) {
      events.add(event);
      latch.countDown();
    }
  }

  @Test
  void deliversQueuedEvents() throws InterruptedException {
    RecordingGateway gateway = new RecordingGateway();
    TelemetryQueue queue =
        new TelemetryQueue(gateway, 8, LoggerFactory.getLogger(TelemetryQueueTest.class));
    queue.start();
    try {
      assertTrue(
          queue.submit(
              new ReportEvent(
                  "req-1",
                  ReportEvent.ReportKind.SESSION_STARTED,
                  "session-1",
                  "uuid",
                  "Alice",
                  "lobby",
                  "",
                  "",
                  "",
                  Instant.now())));
      assertTrue(gateway.latch.await(3, TimeUnit.SECONDS));
      assertEquals(1, gateway.events.size());
      assertEquals(ReportEvent.ReportKind.SESSION_STARTED, gateway.events.get(0).kind());
    } finally {
      queue.close();
    }
  }
}
