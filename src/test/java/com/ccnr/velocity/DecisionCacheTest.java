// Copyright (c) 2026 CCNR
// SPDX-License-Identifier: MIT
package com.ccnr.velocity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DecisionCacheTest {

  @Test
  void returnsEntryBeforeExpiryAndDropsAfter() {
    DecisionCache cache = new DecisionCache(8);
    cache.put("uuid", AccessDecision.allow(60), 1_000L);
    assertTrue(cache.get("uuid", 2_000L).isPresent());
    assertTrue(cache.get("uuid", 61_000L).isEmpty());
  }

  @Test
  void doesNotCacheZeroTtl() {
    DecisionCache cache = new DecisionCache(8);
    cache.put("uuid", AccessDecision.deny("BANNED", "no"), 1_000L);
    assertTrue(cache.get("uuid", 1_000L).isEmpty());
  }

  @Test
  void clearsWhenFull() {
    DecisionCache cache = new DecisionCache(2);
    cache.put("a", AccessDecision.allow(60), 0L);
    cache.put("b", AccessDecision.allow(60), 0L);
    cache.put("c", AccessDecision.allow(60), 0L);
    assertEquals(1, cache.size());
    assertTrue(cache.get("c", 0L).isPresent());
  }
}
