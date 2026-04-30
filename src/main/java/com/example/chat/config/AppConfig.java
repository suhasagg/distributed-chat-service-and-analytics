package com.example.chat.config;

import com.datastax.oss.driver.api.core.CqlSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.net.InetSocketAddress;
import java.time.Duration;

@Configuration
public class AppConfig {
  private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

  @Bean
  CqlSession cql(
      @Value("${app.cassandra-host:cassandra}") String host,
      @Value("${app.cassandra-port:9042}") int port,
      @Value("${app.cassandra-datacenter:datacenter1}") String datacenter) {
    int maxAttempts = 40;
    Duration delay = Duration.ofSeconds(5);

    for (int attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        log.info("Connecting to Cassandra at {}:{} attempt {}/{}", host, port, attempt, maxAttempts);
        return CqlSession.builder()
            .addContactPoint(new InetSocketAddress(host, port))
            .withLocalDatacenter(datacenter)
            .build();
      } catch (Exception e) {
        log.warn("Cassandra not ready at {}:{} attempt {}/{}: {}", host, port, attempt, maxAttempts, e.getMessage());
        if (attempt == maxAttempts) {
          throw e;
        }
        try {
          Thread.sleep(delay.toMillis());
        } catch (InterruptedException interrupted) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException("Interrupted while waiting for Cassandra", interrupted);
        }
      }
    }
    throw new IllegalStateException("Cassandra not reachable");
  }

  @Bean
  DataSource clickDs(
      @Value("${app.clickhouse-url}") String url,
      @Value("${app.clickhouse-user}") String username,
      @Value("${app.clickhouse-password}") String password) {
    DriverManagerDataSource ds = new DriverManagerDataSource();
    ds.setDriverClassName("com.clickhouse.jdbc.ClickHouseDriver");
    ds.setUrl(url);
    ds.setUsername(username);
    ds.setPassword(password);
    return ds;
  }

  @Bean
  JdbcTemplate clickhouse(DataSource clickDs) {
    return new JdbcTemplate(clickDs);
  }
}
