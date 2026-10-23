// Copyright (c) 2026 CCNR
// SPDX-License-Identifier: MIT
package com.ccnr.velocity;

/** CCNR Pass 设备码：代理只保存 login_id，永远不接触 device_code。 */
public record DeviceLoginInfo(
    String loginId, String userCode, String verificationUri, int expiresIn, int interval) {}
