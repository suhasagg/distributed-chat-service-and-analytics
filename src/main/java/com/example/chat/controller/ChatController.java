package com.example.chat.controller;

import com.example.chat.dto.*;
import com.example.chat.service.ChatService;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
public class ChatController {
  private final ChatService service;

  public ChatController(ChatService service) {
    this.service = service;
  }

  @GetMapping("/health")
  public Map<String, Object> health() {
    return Map.of("status", "UP");
  }

  @PostMapping("/rooms")
  public Map<String, Object> createRoom(
      @RequestHeader("X-Tenant-ID") String tenantId,
      @RequestBody RoomRequest request) {
    return service.createRoom(tenantId, request);
  }

  @PostMapping("/messages")
  public MessageResponse sendMessage(
      @RequestHeader("X-Tenant-ID") String tenantId,
      @RequestBody SendMessageRequest request) {
    return service.send(tenantId, request);
  }

  @GetMapping("/rooms/{roomId}/messages")
  public List<MessageResponse> roomHistory(
      @RequestHeader("X-Tenant-ID") String tenantId,
      @PathVariable("roomId") String roomId,
      @RequestParam(defaultValue = "20") int limit) {
    return service.history(tenantId, roomId, limit);
  }

  @GetMapping("/messages/{chatId}")
  public List<MessageResponse> chatHistory(
      @RequestHeader("X-Tenant-ID") String tenantId,
      @PathVariable("chatId") String chatId,
      @RequestParam(defaultValue = "20") int limit) {
    return service.history(tenantId, chatId, limit);
  }

  @PutMapping("/presence")
  public Map<String, Object> updatePresence(
      @RequestHeader("X-Tenant-ID") String tenantId,
      @RequestBody PresenceRequest request) {
    return service.presence(tenantId, request);
  }

  @PostMapping("/presence/{userId}/online")
  public Map<String, Object> setOnline(
      @RequestHeader("X-Tenant-ID") String tenantId,
      @PathVariable("userId") String userId) {
    return service.setPresence(tenantId, userId, "ONLINE");
  }

  @PostMapping("/presence/{userId}/offline")
  public Map<String, Object> setOffline(
      @RequestHeader("X-Tenant-ID") String tenantId,
      @PathVariable("userId") String userId) {
    return service.setPresence(tenantId, userId, "OFFLINE");
  }

  @GetMapping("/presence/{userId}")
  public Map<String, Object> getPresence(
      @RequestHeader("X-Tenant-ID") String tenantId,
      @PathVariable("userId") String userId) {
    return service.getPresence(tenantId, userId);
  }

  @GetMapping("/analytics/rooms")
  public List<Map<String, Object>> analyticsByRoom(@RequestHeader("X-Tenant-ID") String tenantId) {
    return service.analyticsByRoom(tenantId);
  }

  @GetMapping("/analytics/rooms/{roomId}")
  public Map<String, Object> roomAnalytics(
      @RequestHeader("X-Tenant-ID") String tenantId,
      @PathVariable("roomId") String roomId) {
    return service.roomAnalytics(tenantId, roomId);
  }

  @GetMapping("/analytics/chat/{chatId}")
  public Map<String, Object> chatAnalytics(
      @RequestHeader("X-Tenant-ID") String tenantId,
      @PathVariable("chatId") String chatId) {
    return service.roomAnalytics(tenantId, chatId);
  }

  @GetMapping("/analytics/top-users")
  public List<Map<String, Object>> topUsers(@RequestHeader("X-Tenant-ID") String tenantId) {
    return service.topUsers(tenantId);
  }

  @GetMapping("/analytics/messages-per-minute")
  public List<Map<String, Object>> messagesPerMinute(@RequestHeader("X-Tenant-ID") String tenantId) {
    return service.messagesPerMinute(tenantId);
  }
}
