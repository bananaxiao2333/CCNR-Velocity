// Copyright (c) 2026 CCNR
// SPDX-License-Identifier: MIT
package com.ccnr.velocity;

import com.ccnr.control.grpc.v1.AuthorizePlayerRequest;
import com.ccnr.control.grpc.v1.AuthorizePlayerResponse;
import com.ccnr.control.grpc.v1.BeginDeviceLoginRequest;
import com.ccnr.control.grpc.v1.BeginDeviceLoginResponse;
import com.ccnr.control.grpc.v1.ControlServiceGrpc;
import com.ccnr.control.grpc.v1.DeviceLoginStatus;
import com.ccnr.control.grpc.v1.GetDeviceLoginStatusRequest;
import com.ccnr.control.grpc.v1.GetDeviceLoginStatusResponse;
import com.ccnr.control.grpc.v1.ProxyEventKind;
import com.ccnr.control.grpc.v1.ReportProxyEventRequest;
import com.google.protobuf.Timestamp;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Metadata;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.MetadataUtils;
import java.util.concurrent.TimeUnit;

/** control.v1 的同步客户端；所有调用都带 deadline，绝不无限等。 */
public final class GrpcControlGateway implements ControlGateway {

  private static final Metadata.Key<String> AUTHORIZATION =
      Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER);

  private final ManagedChannel channel;
  private final ControlServiceGrpc.ControlServiceBlockingStub stub;
  private final long timeoutMillis;
  private final long authorizeTimeoutMillis;
  private final String proxyId;

  public GrpcControlGateway(PluginConfig config) {
    this.channel = ManagedChannelBuilder.forTarget(config.endpoint()).usePlaintext().build();
    Metadata headers = new Metadata();
    if (!config.token().isEmpty()) {
      headers.put(AUTHORIZATION, "Bearer " + config.token());
    }
    this.stub =
        ControlServiceGrpc.newBlockingStub(channel)
            .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
    this.timeoutMillis = config.timeoutMillis();
    this.authorizeTimeoutMillis = config.authorizeTimeoutMillis();
    this.proxyId = config.proxyId();
  }

  @Override
  public AccessDecision authorize(String playerUuid, String username, String serverId)
      throws ControlUnavailableException {
    AuthorizePlayerRequest request =
        AuthorizePlayerRequest.newBuilder()
            .setPlayerUuid(playerUuid)
            .setUsername(username)
            .setServerId(serverId)
            .setProxyId(proxyId)
            .build();
    try {
      AuthorizePlayerResponse response =
          stub.withDeadlineAfter(authorizeTimeoutMillis, TimeUnit.MILLISECONDS)
              .authorizePlayer(request);
      return new AccessDecision(
          response.getAllowed(),
          reasonName(response.getReason().name()),
          response.getMessage(),
          response.getCacheTtlSeconds());
    } catch (StatusRuntimeException error) {
      throw new ControlUnavailableException("AuthorizePlayer " + error.getStatus(), error);
    }
  }

  @Override
  public void report(ReportEvent event) throws ControlUnavailableException {
    Timestamp occurredAt =
        Timestamp.newBuilder()
            .setSeconds(event.occurredAt().getEpochSecond())
            .setNanos(event.occurredAt().getNano())
            .build();
    ReportProxyEventRequest request =
        ReportProxyEventRequest.newBuilder()
            .setRequestId(event.requestId())
            .setKind(kind(event.kind()))
            .setSessionId(event.sessionId())
            .setPlayerUuid(event.playerUuid())
            .setUsername(event.username())
            .setServerId(event.serverId())
            .setFromServerId(event.fromServerId())
            .setOccurredAt(occurredAt)
            .setDenyReason(event.denyReason())
            .setEndReason(event.endReason())
            .build();
    try {
      stub.withDeadlineAfter(timeoutMillis, TimeUnit.MILLISECONDS).reportProxyEvent(request);
    } catch (StatusRuntimeException error) {
      throw new ControlUnavailableException("ReportProxyEvent " + error.getStatus(), error);
    }
  }

  @Override
  public DeviceLoginInfo beginDeviceLogin(String playerUuid, String username, String proxyId)
      throws ControlUnavailableException {
    BeginDeviceLoginRequest request =
        BeginDeviceLoginRequest.newBuilder()
            .setPlayerUuid(playerUuid)
            .setUsername(username)
            .setProxyId(proxyId)
            .build();
    try {
      BeginDeviceLoginResponse response =
          stub.withDeadlineAfter(authorizeTimeoutMillis, TimeUnit.MILLISECONDS)
              .beginDeviceLogin(request);
      return new DeviceLoginInfo(
          response.getLoginId(),
          response.getUserCode(),
          response.getVerificationUri(),
          response.getExpiresIn(),
          response.getInterval());
    } catch (StatusRuntimeException error) {
      throw new ControlUnavailableException("BeginDeviceLogin " + error.getStatus(), error);
    }
  }

  @Override
  public DeviceLoginState deviceLoginStatus(String loginId) throws ControlUnavailableException {
    GetDeviceLoginStatusRequest request =
        GetDeviceLoginStatusRequest.newBuilder().setLoginId(loginId).build();
    try {
      GetDeviceLoginStatusResponse response =
          stub.withDeadlineAfter(timeoutMillis, TimeUnit.MILLISECONDS)
              .getDeviceLoginStatus(request);
      return new DeviceLoginState(
          state(response.getStatus()), response.getCasdoorUserId(), response.getMessage());
    } catch (StatusRuntimeException error) {
      throw new ControlUnavailableException("GetDeviceLoginStatus " + error.getStatus(), error);
    }
  }

  private static DeviceLoginState.State state(DeviceLoginStatus status) {
    return switch (status) {
      case DEVICE_LOGIN_STATUS_PENDING -> DeviceLoginState.State.PENDING;
      case DEVICE_LOGIN_STATUS_APPROVED -> DeviceLoginState.State.APPROVED;
      case DEVICE_LOGIN_STATUS_DENIED -> DeviceLoginState.State.DENIED;
      case DEVICE_LOGIN_STATUS_EXPIRED -> DeviceLoginState.State.EXPIRED;
      default -> DeviceLoginState.State.UNKNOWN;
    };
  }

  private static String reasonName(String enumName) {
    String prefix = "AUTHORIZE_REASON_";
    return enumName.startsWith(prefix) ? enumName.substring(prefix.length()) : enumName;
  }

  private static ProxyEventKind kind(ReportEvent.ReportKind kind) {
    return switch (kind) {
      case SESSION_STARTED -> ProxyEventKind.PROXY_EVENT_KIND_SESSION_STARTED;
      case SESSION_HEARTBEAT -> ProxyEventKind.PROXY_EVENT_KIND_SESSION_HEARTBEAT;
      case SERVER_SWITCHED -> ProxyEventKind.PROXY_EVENT_KIND_SERVER_SWITCHED;
      case SESSION_ENDED -> ProxyEventKind.PROXY_EVENT_KIND_SESSION_ENDED;
      case LOGIN_DENIED -> ProxyEventKind.PROXY_EVENT_KIND_LOGIN_DENIED;
    };
  }

  @Override
  public void close() {
    channel.shutdownNow();
  }
}
