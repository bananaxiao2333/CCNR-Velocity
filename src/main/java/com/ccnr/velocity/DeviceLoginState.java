// Copyright (c) 2026 CCNR
// SPDX-License-Identifier: MIT
package com.ccnr.velocity;

/** 设备码登录状态；APPROVED 之后仍需走一次 AuthorizePlayer 才能进游戏服。 */
public record DeviceLoginState(State state, String casdoorUserId, String message) {

  public enum State {
    PENDING,
    APPROVED,
    DENIED,
    EXPIRED,
    UNKNOWN
  }
}
