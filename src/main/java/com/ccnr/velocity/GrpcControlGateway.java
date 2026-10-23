// Copyright (c) 2026 CCNR
// SPDX-License-Identifier: MIT
package com.ccnr.velocity;

import com.ccnr.control.grpc.v1.AuthorizePlayerRequest;
import com.ccnr.control.grpc.v1.AuthorizePlayerResponse;
import com.ccnr.control.grpc.v1.ControlServiceGrpc;
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
  }

  @Override
  public AccessDecision authorize(String playerUuid, String username, String serverId)
      throws ControlUnavailableException {
    AuthorizePlayerRequest request =
        AuthorizePlayerRequest.newBuilder()
            .setPlayerUuid(playerUuid)
            .setUsername(username)
            .setServerId(serverId)
            .setProxyId("velocity")
            .build();
    try {
      AuthorizePlayerResponse response =
          stub.withDeadlineAfter(timeoutMillis, TimeUnit.MILLISECONDS).authorizePlayer(request);
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
