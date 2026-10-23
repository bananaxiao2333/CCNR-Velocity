// Copyright (c) 2026 CCNR
// SPDX-License-Identifier: MIT
package com.ccnr.velocity;

/** 控制面客户端的最小接口：测试可以给假实现，生产走 gRPC。 */
public interface ControlGateway extends AutoCloseable {

  AccessDecision authorize(String playerUuid, String username, String serverId)
      throws ControlUnavailableException;

  void report(ReportEvent event) throws ControlUnavailableException;

  DeviceLoginInfo beginDeviceLogin(String playerUuid, String username, String proxyId)
      throws ControlUnavailableException;

  DeviceLoginState deviceLoginStatus(String loginId) throws ControlUnavailableException;

  @Override
  default void close() {}
}
