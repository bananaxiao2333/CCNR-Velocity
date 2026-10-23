// Copyright (c) 2026 CCNR
// SPDX-License-Identifier: MIT
package com.ccnr.velocity;

/** 控制面不可达或返回非业务错误；由调用方按 fail-open 策略兜底。 */
public class ControlUnavailableException extends Exception {

  private static final long serialVersionUID = 1L;

  public ControlUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
