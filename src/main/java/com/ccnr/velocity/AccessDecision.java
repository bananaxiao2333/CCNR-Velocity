// Copyright (c) 2026 CCNR
// SPDX-License-Identifier: MIT
package com.ccnr.velocity;

/** 一次准入决策；cacheTtlSeconds=0 表示不要缓存。 */
public record AccessDecision(boolean allowed, String reason, String message, int cacheTtlSeconds) {

  public static AccessDecision allow(int cacheTtlSeconds) {
    return new AccessDecision(true, "ALLOWED", "", cacheTtlSeconds);
  }

  public static AccessDecision deny(String reason, String message) {
    return new AccessDecision(false, reason, message, 0);
  }
}
