package com.example.chat.dto; import java.time.Instant; import java.util.Map;
public record MessageResponse(String messageId,String tenantId,String roomId,String senderId,String messageType,String content,Instant createdAt,Map<String,Object> metadata){}
