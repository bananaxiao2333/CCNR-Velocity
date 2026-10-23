// Copyright (c) 2026 CCNR
// SPDX-License-Identifier: MIT
package com.ccnr.velocity;

import java.util.Optional;
import java.util.function.LongSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 准入编排：先查缓存，失败按 fail-open 兜底。 */
public final class AccessController {

  private static final Logger LOGGER = LoggerFactory.getLogger(AccessController.class);

  private final ControlGateway gateway;
  private final DecisionCache cache;
  private final boolean failOpen;
  private final LongSupplier clock;

  public AccessController(ControlGateway gateway, boolean failOpen, int maxCacheEntries) {
    this(gateway, failOpen, maxCacheEntries, System::currentTimeMillis);
  }

  AccessController(
      ControlGateway gateway, boolean failOpen, int maxCacheEntries, LongSupplier clock) {
    this.gateway = gateway;
    this.cache = new DecisionCache(maxCacheEntries);
    this.failOpen = failOpen;
    this.clock = clock;
  }

  public AccessDecision authorize(String playerUuid, String username, String serverId) {
    long now = clock.getAsLong();
    Optional<AccessDecision> cached = cache.get(playerUuid, now);
    if (cached.isPresent()) {
      return cached.get();
    }
    try {
      AccessDecision decision = gateway.authorize(playerUuid, username, serverId);
      cache.put(playerUuid, decision, now);
      return decision;
    } catch (ControlUnavailableException error) {
      LOGGER.warn("准入 RPC 失败：{}", error.getMessage(), error);
      if (failOpen) {
        return AccessDecision.allow(0);
      }
      return AccessDecision.deny("CASDOOR_UNAVAILABLE", "控制面暂时不可用，请稍后再试");
    }
  }

  public int cachedPlayers() {
    return cache.size();
  }
}
