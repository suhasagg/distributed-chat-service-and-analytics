package com.example.chat.service;

import com.datastax.oss.driver.api.core.CqlSession;
import com.example.chat.dto.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class ChatService {
  private final CqlSession cql;
  private final KafkaTemplate<String, String> kafka;
  private final StringRedisTemplate redis;
  private final JdbcTemplate clickhouse;

  public ChatService(CqlSession cql, KafkaTemplate<String, String> kafka, StringRedisTemplate redis, JdbcTemplate clickhouse) {
    this.cql = cql;
    this.kafka = kafka;
    this.redis = redis;
    this.clickhouse = clickhouse;
  }

  public Map<String, Object> createRoom(String tenantId, RoomRequest request) {
    String members = String.join(",", request.members() == null ? List.of() : request.members());
    cql.execute(
        "INSERT INTO chat.rooms_by_tenant(tenant_id,room_id,members,created_at) VALUES(?,?,?,?)",
        tenantId, request.roomId(), members, Instant.now());
    return Map.of(
        "tenantId", tenantId,
        "roomId", request.roomId(),
        "members", request.members() == null ? List.of() : request.members());
  }

  public MessageResponse send(String tenantId, SendMessageRequest request) {
    String roomId = request.roomId();
    if (roomId == null || roomId.isBlank()) {
      throw new IllegalArgumentException("chatId or roomId is required");
    }
    if (request.senderId() == null || request.senderId().isBlank()) {
      throw new IllegalArgumentException("senderId is required");
    }
    if (request.content() == null || request.content().isBlank()) {
      throw new IllegalArgumentException("content is required");
    }

    String messageId = UUID.randomUUID().toString();
    Instant now = Instant.now();
    String bucket = now.toString().substring(0, 10);
    String messageType = request.messageType() == null ? "TEXT" : request.messageType();
    String metadata = request.metadata() == null ? "{}" : request.metadata().toString();

    cql.execute(
        "INSERT INTO chat.messages_by_room(tenant_id,room_id,bucket,created_at,message_id,sender_id,message_type,content,metadata) " +
            "VALUES(?,?,?,?,?,?,?,?,?)",
        tenantId, roomId, bucket, now, messageId, request.senderId(), messageType, request.content(), metadata);

    String event = String.format(
        "{\"tenantId\":\"%s\",\"roomId\":\"%s\",\"messageId\":\"%s\",\"senderId\":\"%s\"}",
        tenantId, roomId, messageId, request.senderId());
    kafka.send("chat-message-events", tenantId + ":" + roomId, event);

    try {
      clickhouse.update(
          "INSERT INTO message_events VALUES(?,?,?,?,?,?)",
          tenantId, roomId, request.senderId(), messageType, Timestamp.from(now), 1L);
    } catch (Exception ignored) {
      // Analytics must not break the transactional chat write path in the local demo.
    }

    return new MessageResponse(messageId, tenantId, roomId, request.senderId(), messageType, request.content(), now, request.metadata());
  }

  public List<MessageResponse> history(String tenantId, String roomId, int limit) {
    String bucket = Instant.now().toString().substring(0, 10);
    var rows = cql.execute(
        "SELECT * FROM chat.messages_by_room WHERE tenant_id=? AND room_id=? AND bucket=? LIMIT ?",
        tenantId, roomId, bucket, limit);

    List<MessageResponse> out = new ArrayList<>();
    rows.forEach(row -> out.add(new MessageResponse(
        row.getString("message_id"),
        tenantId,
        roomId,
        row.getString("sender_id"),
        row.getString("message_type"),
        row.getString("content"),
        row.getInstant("created_at"),
        Map.of())));
    return out;
  }

  public Map<String, Object> presence(String tenantId, PresenceRequest request) {
    return setPresence(tenantId, request.userId(), request.status());
  }

  public Map<String, Object> setPresence(String tenantId, String userId, String status) {
    String normalized = status == null ? "OFFLINE" : status.toUpperCase(Locale.ROOT);
    String key = "presence:" + tenantId + ":" + userId;
    if ("OFFLINE".equals(normalized)) {
      redis.delete(key);
      return Map.of("tenantId", tenantId, "userId", userId, "status", "OFFLINE");
    }
    redis.opsForValue().set(key, normalized, 60, TimeUnit.SECONDS);
    return Map.of("tenantId", tenantId, "userId", userId, "status", normalized);
  }

  public Map<String, Object> getPresence(String tenantId, String userId) {
    String status = redis.opsForValue().get("presence:" + tenantId + ":" + userId);
    return Map.of("tenantId", tenantId, "userId", userId, "status", status == null ? "OFFLINE" : status);
  }

  public List<Map<String, Object>> analyticsByRoom(String tenantId) {
    try {
      return clickhouse.queryForList(
          "SELECT room_id AS roomId, sum(message_count) AS messageCount " +
              "FROM message_events WHERE tenant_id=? GROUP BY room_id ORDER BY messageCount DESC",
          tenantId);
    } catch (Exception e) {
      return errorList(e);
    }
  }

  public Map<String, Object> roomAnalytics(String tenantId, String roomId) {
    try {
      List<Map<String, Object>> rows = clickhouse.queryForList(
          "SELECT room_id AS roomId, sum(message_count) AS messageCount, uniqExact(sender_id) AS activeUsers " +
              "FROM message_events WHERE tenant_id=? AND room_id=? GROUP BY room_id",
          tenantId, roomId);
      if (rows.isEmpty()) {
        return Map.of("roomId", roomId, "chatId", roomId, "messageCount", 0, "activeUsers", 0);
      }
      Map<String, Object> row = rows.get(0);
      return Map.of(
          "roomId", roomId,
          "chatId", roomId,
          "messageCount", row.get("messageCount"),
          "activeUsers", row.get("activeUsers"));
    } catch (Exception e) {
      return Map.of("roomId", roomId, "chatId", roomId, "messageCount", 0, "activeUsers", 0, "error", e.getMessage());
    }
  }

  public List<Map<String, Object>> topUsers(String tenantId) {
    try {
      return clickhouse.queryForList(
          "SELECT sender_id AS userId, sum(message_count) AS messages " +
              "FROM message_events WHERE tenant_id=? GROUP BY sender_id ORDER BY messages DESC LIMIT 10",
          tenantId);
    } catch (Exception e) {
      return errorList(e);
    }
  }

  public List<Map<String, Object>> messagesPerMinute(String tenantId) {
    try {
      return clickhouse.queryForList(
          "SELECT toStartOfMinute(event_time) AS minute, sum(message_count) AS count " +
              "FROM message_events WHERE tenant_id=? GROUP BY minute ORDER BY minute DESC LIMIT 60",
          tenantId);
    } catch (Exception e) {
      return errorList(e);
    }
  }

  private List<Map<String, Object>> errorList(Exception e) {
    Map<String, Object> error = new LinkedHashMap<>();
    error.put("error", "analytics_unavailable");
    error.put("message", e.getMessage());
    return List.of(error);
  }
}
