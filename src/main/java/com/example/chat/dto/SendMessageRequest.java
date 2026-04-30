package com.example.chat.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.Map;

public record SendMessageRequest(
    @JsonAlias({"chatId", "roomId"}) String roomId,
    String senderId,
    String messageType,
    String content,
    Map<String,Object> metadata
) {}
