package com.example.chat.service;

import com.datastax.oss.driver.api.core.CqlSession;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SchemaService {
  private static final Logger log = LoggerFactory.getLogger(SchemaService.class);
  private final CqlSession cql;
  private final JdbcTemplate ch;

  public SchemaService(CqlSession cql, JdbcTemplate ch) {
    this.cql = cql;
    this.ch = ch;
  }

  @PostConstruct
  public void init() {
    initCassandra();
    initClickHouse();
  }

  private void initCassandra() {
    try {
      cql.execute("CREATE KEYSPACE IF NOT EXISTS chat WITH replication={'class':'SimpleStrategy','replication_factor':1}");
      cql.execute("CREATE TABLE IF NOT EXISTS chat.messages_by_room(" +
          "tenant_id text, room_id text, bucket text, created_at timestamp, message_id text, " +
          "sender_id text, message_type text, content text, metadata text, " +
          "PRIMARY KEY((tenant_id,room_id,bucket),created_at,message_id)) " +
          "WITH CLUSTERING ORDER BY(created_at DESC)");
      cql.execute("CREATE TABLE IF NOT EXISTS chat.rooms_by_tenant(" +
          "tenant_id text, room_id text, members text, created_at timestamp, PRIMARY KEY(tenant_id,room_id))");
      log.info("Cassandra schema initialized");
    } catch (Exception e) {
      log.warn("Cassandra schema initialization skipped: {}", e.getMessage());
    }
  }

  private void initClickHouse() {
    try {
      ch.execute("CREATE TABLE IF NOT EXISTS message_events(" +
          "tenant_id String, room_id String, sender_id String, message_type String, " +
          "event_time DateTime, message_count UInt64) " +
          "ENGINE=MergeTree ORDER BY (tenant_id, room_id, event_time)");
      log.info("ClickHouse schema initialized");
    } catch (Exception e) {
      log.warn("ClickHouse schema initialization skipped: {}", e.getMessage());
    }
  }
}
