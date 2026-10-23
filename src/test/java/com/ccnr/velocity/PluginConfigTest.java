// Copyright (c) 2026 CCNR
// SPDX-License-Identifier: MIT
package com.ccnr.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginConfigTest {

  @TempDir Path directory;

  private Path write(String body) throws IOException {
    Path file = directory.resolve("config.properties");
    Files.writeString(file, body);
    return file;
  }

  @Test
  void loadsDefaultsAndExplicitValues() throws IOException {
    Path file =
        write(
            """
                        endpoint=10.0.0.5:8081
                        token=s3cret
                        fail-open=true
                        timeout-millis=1500
                        cache-ttl-seconds=30
                        heartbeat-seconds=45
                        queue-capacity=1024
                        proxy-id=proxy-a
                        """);
    PluginConfig config = PluginConfig.load(file);
    assertEquals("10.0.0.5:8081", config.endpoint());
    assertEquals("s3cret", config.token());
    assertTrue(config.failOpen());
    assertEquals(1500, config.timeoutMillis());
    assertEquals(30, config.cacheTtlSeconds());
    assertEquals(45, config.heartbeatSeconds());
    assertEquals(1024, config.queueCapacity());
    assertEquals("proxy-a", config.proxyId());
  }

  @Test
  void rejectsMissingEndpoint() throws IOException {
    Path file =
        write(
            """
                        fail-open=true
                        """);
    IllegalArgumentException error =
        assertThrows(IllegalArgumentException.class, () -> PluginConfig.load(file));
    assertTrue(error.getMessage().contains("endpoint"));
  }

  @Test
  void rejectsOutOfRangeTimeout() throws IOException {
    Path file =
        write(
            """
                        endpoint=127.0.0.1:8081
                        timeout-millis=1
                        """);
    assertThrows(IllegalArgumentException.class, () -> PluginConfig.load(file));
  }

  @Test
  void rejectsNonBooleanFailOpen() throws IOException {
    Path file =
        write(
            """
                        endpoint=127.0.0.1:8081
                        fail-open=maybe
                        """);
    assertThrows(IllegalArgumentException.class, () -> PluginConfig.load(file));
  }
}
