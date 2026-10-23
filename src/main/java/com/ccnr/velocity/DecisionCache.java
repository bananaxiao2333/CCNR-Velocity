// Copyright (c) 2026 CCNR
// SPDX-License-Identifier: MIT
package com.ccnr.velocity;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** 按 UUID 缓存准入结果；有上限，满了整体清空（登录风暴下宁可多查一次）。 */
public final class DecisionCache {

  private record Entry(AccessDecision decision, long expiresAtMillis) {}

  private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
  private final int maxEntries;

  public DecisionCache(int maxEntries) {
    if (maxEntries <= 0) {
      throw new IllegalArgumentException("maxEntries 必须为正");
    }
    this.maxEntries = maxEntries;
  }

  public Optional<AccessDecision> get(String playerUuid, long nowMillis) {
    Entry entry = entries.get(playerUuid);
    if (entry == null) {
      return Optional.empty();
    }
    if (entry.expiresAtMillis() <= nowMillis) {
      entries.remove(playerUuid, entry);
      return Optional.empty();
    }
    return Optional.of(entry.decision());
  }

  public void put(String playerUuid, AccessDecision decision, long nowMillis) {
    if (decision.cacheTtlSeconds() <= 0) {
      return;
    }
    if (entries.size() >= maxEntries) {
      entries.clear();
    }
    entries.put(playerUuid, new Entry(decision, nowMillis + decision.cacheTtlSeconds() * 1000L));
  }

  public int size() {
    return entries.size();
  }
}
