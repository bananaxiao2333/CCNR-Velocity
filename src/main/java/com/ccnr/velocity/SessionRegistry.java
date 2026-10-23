// Copyright (c) 2026 CCNR
// SPDX-License-Identifier: MIT
package com.ccnr.velocity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 在线会话状态：一个玩家同一时刻只有一条会话。 */
public final class SessionRegistry {

  public record Session(String sessionId, String serverId) {}

  public record Switched(Session session, String fromServerId) {}

  private final ConcurrentHashMap<String, Session> sessions = new ConcurrentHashMap<>();

  public Optional<Session> start(String playerUuid, String serverId) {
    Session created = new Session(UUID.randomUUID().toString(), serverId);
    return sessions.putIfAbsent(playerUuid, created) == null
        ? Optional.of(created)
        : Optional.empty();
  }

  public Optional<Session> current(String playerUuid) {
    return Optional.ofNullable(sessions.get(playerUuid));
  }

  public Optional<Switched> switchServer(String playerUuid, String serverId) {
    Session current = sessions.get(playerUuid);
    if (current == null || current.serverId().equals(serverId)) {
      return Optional.empty();
    }
    Session updated = new Session(current.sessionId(), serverId);
    if (sessions.replace(playerUuid, current, updated)) {
      return Optional.of(new Switched(updated, current.serverId()));
    }
    return Optional.empty();
  }

  public Optional<Session> end(String playerUuid) {
    return Optional.ofNullable(sessions.remove(playerUuid));
  }

  public List<Map.Entry<String, Session>> snapshot() {
    return new ArrayList<>(sessions.entrySet());
  }

  public int size() {
    return sessions.size();
  }
}
