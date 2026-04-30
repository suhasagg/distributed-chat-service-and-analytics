# Distributed Chat and Analytics Service

- A multi-tenant, event-driven chat backend designed to demonstrate production-oriented architecture for high-scale messaging, presence, and analytics systems.

This project implements:

- Cassandra as the primary chat message store
- Kafka for asynchronous message event streaming
- Redis for user presence and low-latency state
- ClickHouse as an OLAP analytics store
- Spring Boot for the API layer
- Multi-tenant isolation using X-Tenant-ID
- Chat message APIs
- Presence APIs
- Analytics APIs



## 1. Problem Statement

This service is designed for a scenario where a company needs to:

- support real-time or near-real-time chat across many tenants
- store large volumes of chat messages efficiently
- fetch recent room/chat history with low latency
- track user online/offline presence
- stream message events for downstream consumers
- run analytics such as message count per room and top active users
- isolate tenant data safely
- scale write-heavy and read-heavy paths independently
- demonstrate enterprise-grade design patterns

The system separates:

- message ingestion
- durable message storage
- event streaming
- presence state
- OLAP analytics

- This makes the write path scalable, the read path efficient, and the analytics path independent from user-facing chat latency.

## 2. Architecture Overview
- High-level architecture
```text
                           +----------------------+
                           |       Clients        |
                           | Web / Mobile / Admin |
                           +----------+-----------+
                                      |
                                      v
                         +------------+-------------+
                         |      Spring Boot API     |
                         | Messages + Presence +    |
                         | Analytics + Health       |
                         +------+---------+---------+
                                |         |
                    Write path  |         | Presence path
                                |         |
                                v         v
                    +-----------+--+   +--+------------------+
                    | Cassandra    |   | Redis               |
                    | Message      |   | Online/offline      |
                    | Store        |   | presence            |
                    +------+-------+   +-----+---------------+
                           |
                           |
                           v
                    +------+-----------------+------+
                    |      Kafka Message Events     |
                    | chat-messages topic           |
                    +---------------+---------------+
                                    |
                                    v
                         +----------+-----------+
                         | Analytics Consumer / |
                         | Event Processor      |
                         +----------+-----------+
                                    |
                                    v
                         +----------+-----------+
                         |      ClickHouse      |
                         | OLAP analytics store |
                         +----------------------+
```
                         
- Core architectural choices
- Cassandra

- Used as the primary message store because chat workloads are write-heavy, append-heavy, and usually query messages by room/chat ordered by time.

- Kafka

- Used to decouple message writes from downstream processing such as analytics, notifications, moderation, audit, and search indexing.

- Redis

- Used for presence because online/offline state is short-lived, frequently updated, and latency-sensitive.

- ClickHouse

- Used as the analytics store because OLAP queries such as message counts, top users, active rooms, and time-series aggregation should not run against Cassandra.

## 3. Key Features
- Functional features
- Send chat message
- Fetch chat room message history
- Set user online
- Set user offline
- Get user presence
- Room-level analytics
- Top user analytics
- Health check
- Multi-tenant isolation
- Platform features
- Cassandra-backed message persistence
- Kafka-backed event streaming
- Redis-backed presence tracking
- ClickHouse-backed OLAP analytics
- Docker Compose local environment
- Tenant-scoped APIs
- Cassandra startup retry logic
- ClickHouse schema initialization
- Reduced host port conflicts by exposing only API port
- Assessment bonus features included
- Cassandra data modeling
- Kafka event pipeline
- Redis presence model
- ClickHouse analytics model
- Production readiness guidance
- Cost optimization guidance
- Interview talking points

## 4. Consistency Model

- The system intentionally uses a mixed consistency model.

- Strong consistency
- accepted message is written to Cassandra
- message history reads come from Cassandra
- presence writes update Redis immediately
- Eventual consistency
- Kafka message event propagation
- ClickHouse analytics visibility
- downstream consumers such as notifications/search/moderation
- Why this trade-off was chosen

Synchronous writes to Cassandra, Kafka, Redis, and ClickHouse in a single request would:

- increase message send latency
- tightly couple user-facing chat to analytics availability
- reduce resilience
- make provider/downstream failures visible to chat users

- Instead, this design writes the message to Cassandra first, then emits an event for asynchronous processing.

## 5. Multi-Tenancy Strategy

- Tenant isolation is enforced at multiple layers.

- Tenant identification

- The API uses the X-Tenant-ID header.

- Isolation enforcement

Tenant scope is applied in:

- message creation
- message history lookup
- Redis presence key namespace
- Kafka event payload
- Cassandra partition key
- ClickHouse analytics dimensions
- metrics and logs
- Why this matters

This prevents accidental cross-tenant leakage in:

- chat message history
- user presence
- analytics reports
- downstream event processing
- cache/state pollution

Example tenant-scoped keys:

- presence:tenant-a:user-1
- presence:tenant-b:user-1

Example Cassandra partitioning concept:

- tenant_id + room_id

## 6. API List
6.1 Health Check

- Endpoint
- GET /health

- Example curl

```bash
- curl http://localhost:8080/health
```
```

Response:

```json
{
  "status": "UP"
}
```
6.2 Send Message

- Endpoint
- POST /messages

- Headers

- X-Tenant-ID: tenant-a
- Content-Type: application/json

- Request body

{
  "chatId": "chat-1",
  "senderId": "user-1",
  "content": "Hello world!"
}

- The API accepts chatId and stores it internally as roomId.

- Example curl

```bash
- curl -X POST http://localhost:8080/messages \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: tenant-a" \
  -d '{
    "chatId": "chat-1",
    "senderId": "user-1",
    "content": "Hello world!"
  }'
```

- Example response

```json
{
  "messageId": "2eb4dbdd-c026-48e7-b028-6edaa7dc103b",
  "tenantId": "tenant-a",
  "roomId": "chat-1",
  "senderId": "user-1",
  "messageType": null,
  "content": "Hello world!",
  "createdAt": "2026-04-30T06:32:04.242118976Z",
  "metadata": null
}
```
6.3 Fetch Messages for Chat Room

- Endpoint
- GET /messages/{chatId}?limit={limit}

- Headers

- X-Tenant-ID: tenant-a

- Example curl

```bash
- curl "http://localhost:8080/messages/chat-1?limit=10" \
  -H "X-Tenant-ID: tenant-a"
```

- Example response

```json
[
  {
    "messageId": "2eb4dbdd-c026-48e7-b028-6edaa7dc103b",
    "tenantId": "tenant-a",
    "roomId": "chat-1",
    "senderId": "user-1",
    "messageType": null,
    "content": "Hello world!",
    "createdAt": "2026-04-30T06:32:04.242118976Z",
    "metadata": {}
  }
]
```
6.4 Send Multiple Messages

- Example curl

```bash
- for i in {1..10}; do
- curl -X POST http://localhost:8080/messages \
    -H "Content-Type: application/json" \
    -H "X-Tenant-ID: tenant-a" \
    -d "{\"chatId\":\"chat-1\",\"senderId\":\"user-1\",\"content\":\"msg-$i\"}"
- done
```

- Expected behavior

10 messages are inserted into Cassandra
- message events are published to Kafka
- analytics can be queried through the analytics APIs
6.5 Set User Online

- Endpoint
- POST /presence/{userId}/online

- Headers

- X-Tenant-ID: tenant-a

- Example curl

```bash
- curl -X POST http://localhost:8080/presence/user-1/online \
  -H "X-Tenant-ID: tenant-a"
```

- Example response

```json
{
  "tenantId": "tenant-a",
  "userId": "user-1",
  "status": "ONLINE"
}
```
6.6 Set User Offline

- Endpoint
- POST /presence/{userId}/offline

- Headers

- X-Tenant-ID: tenant-a

- Example curl

```bash
- curl -X POST http://localhost:8080/presence/user-1/offline \
  -H "X-Tenant-ID: tenant-a"
```

- Example response

```json
{
  "tenantId": "tenant-a",
  "userId": "user-1",
  "status": "OFFLINE"
}
```
6.7 Get User Presence

- Endpoint
- GET /presence/{userId}

- Headers

- X-Tenant-ID: tenant-a

- Example curl

```bash
- curl http://localhost:8080/presence/user-1 \
  -H "X-Tenant-ID: tenant-a"
```

- Example response

```json
{
  "tenantId": "tenant-a",
  "userId": "user-1",
  "status": "ONLINE"
}
```
6.8 Room Analytics

- Endpoint
- GET /analytics/rooms/{roomId}

- Headers

- X-Tenant-ID: tenant-a

- Example curl

```bash
- curl http://localhost:8080/analytics/rooms/chat-1 \
  -H "X-Tenant-ID: tenant-a"
```

- Example response

```json
{
  "tenantId": "tenant-a",
  "roomId": "chat-1",
  "messageCount": 11
}
```
6.9 Top Users Analytics

- Endpoint
- GET /analytics/top-users

- Headers

- X-Tenant-ID: tenant-a

- Example curl

```bash
- curl http://localhost:8080/analytics/top-users \
  -H "X-Tenant-ID: tenant-a"
```

- Example response

```json
[
  {
    "tenantId": "tenant-a",
    "senderId": "user-1",
    "messageCount": 11
  }
]
```
## 7. Project Structure
.
├── Dockerfile
├── docker-compose.yml
├── pom.xml
├── README.md
├── benchmarks/
│   └── k6-chat-smoke.js
└── src/
    ├── main/java/com/example/chat/
    │   ├── controller/
    │   ├── service/
    │   ├── config/
    │   ├── dto/
    │   └── domain/
    └── main/resources/
        └── application.yml
        
## 8. How the Code Works
- Controllers
- ChatController

Handles:

- send message
- fetch messages by chat/room

- This controller delegates message persistence and event publication to ChatService.

- PresenceController

Handles:

- set user online
- set user offline
- get user presence

- This controller delegates low-latency state management to PresenceService.

- AnalyticsController

Handles:

- room analytics
- top users analytics

- This controller reads aggregated message events from ClickHouse through AnalyticsService.

- HealthController

Handles:

- basic application health check
- Services
- ChatService

Responsible for:

- accepting tenant-scoped message requests
- converting chatId to internal roomId
- generating message ID
- writing message to Cassandra
- publishing chat message event to Kafka
- writing analytics event to ClickHouse or analytics pipeline
- returning message response

Important implementation details:

- Cassandra timestamp fields use java.time.Instant
- Docker service DNS uses cassandra:9042
- chatId and roomId are normalized so API and storage naming do not break inserts
- PresenceService

Responsible for:

- storing online/offline state in Redis
- using tenant-scoped Redis keys
- returning current presence state

Example Redis key:

- presence:tenant-a:user-1
- AnalyticsService

Responsible for:

- reading message count per room from ClickHouse
- reading top active users from ClickHouse
- isolating analytics queries by tenant ID
- SchemaService

Responsible for:

- initializing Cassandra keyspace/table
- initializing ClickHouse analytics table
- retrying Cassandra connection during startup
- avoiding fragile startup failures when Cassandra is still warming up

## 9. Local Setup and Run Instructions
- Prerequisites
- Docker
- Docker Compose
- Java 17 and Maven only if running outside Docker
- Optional: k6 for load testing
- Run with Docker Compose
```bash
- docker compose up -d --build
```
```bash
- docker compose logs -f app
```
- To reset state
```bash
- docker compose down -v
```
```bash
- docker compose up -d --build
```
- First-boot Cassandra note

- Cassandra can take 2–4 minutes on first boot.

The app includes retry logic and logs messages like:

- Connecting to Cassandra at cassandra:9042 attempt 1/40

Wait until you see:

- Cassandra schema initialized
- ClickHouse schema initialized
- Started ChatApplication

## 10. Full Demo and Testing Steps
10.1 Start clean
```bash
- docker compose down -v
```
```bash
- docker compose up -d --build
```
```bash
- docker compose logs -f app
```
10.2 Health check
```bash
- curl http://localhost:8080/health
```

Expected:

```json
{
  "status": "UP"
}
```
10.3 Create a message
```bash
- curl -X POST http://localhost:8080/messages \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: tenant-a" \
  -d '{
    "chatId": "chat-1",
    "senderId": "user-1",
    "content": "Hello world!"
  }'
```

Expected:

```json
{
  "tenantId": "tenant-a",
  "roomId": "chat-1",
  "senderId": "user-1",
  "content": "Hello world!"
}
```
10.4 Fetch messages
```bash
- curl "http://localhost:8080/messages/chat-1?limit=10" \
  -H "X-Tenant-ID: tenant-a"
```

Expected:

```json
[
  {
    "tenantId": "tenant-a",
    "roomId": "chat-1",
    "senderId": "user-1",
    "content": "Hello world!"
  }
]
```
10.5 Load multiple messages
```bash
- for i in {1..10}; do
- curl -X POST http://localhost:8080/messages \
    -H "Content-Type: application/json" \
    -H "X-Tenant-ID: tenant-a" \
    -d "{\"chatId\":\"chat-1\",\"senderId\":\"user-1\",\"content\":\"msg-$i\"}"
- done
```
10.6 Set user online
```bash
- curl -X POST http://localhost:8080/presence/user-1/online \
  -H "X-Tenant-ID: tenant-a"
```

Expected:

```json
{
  "tenantId": "tenant-a",
  "userId": "user-1",
  "status": "ONLINE"
}
```
10.7 Get user presence
```bash
- curl http://localhost:8080/presence/user-1 \
  -H "X-Tenant-ID: tenant-a"
```

Expected:

```json
{
  "tenantId": "tenant-a",
  "userId": "user-1",
  "status": "ONLINE"
}
```
10.8 Set user offline
```bash
- curl -X POST http://localhost:8080/presence/user-1/offline \
  -H "X-Tenant-ID: tenant-a"
```

Expected:

```json
{
  "tenantId": "tenant-a",
  "userId": "user-1",
  "status": "OFFLINE"
}
```
10.9 Verify Cassandra keyspaces
```bash
- docker compose exec cassandra cqlsh -e "DESCRIBE KEYSPACES;"
```
10.10 Verify Cassandra tables
```bash
- docker compose exec cassandra cqlsh -e "DESCRIBE KEYSPACE chat;"
```
10.11 Query Cassandra messages
```bash
- docker compose exec cassandra cqlsh -e "

- SELECT tenant_id, room_id, message_id, sender_id, content, created_at
- FROM chat.messages_by_room
- WHERE tenant_id='tenant-a' AND room_id='chat-1'
- LIMIT 10;
"
```
10.12 Verify Kafka topics
```bash
- docker compose exec kafka kafka-topics \
  --bootstrap-server kafka:29092 \
  --list
```

Expected:

- chat-messages
10.13 Consume Kafka messages
```bash
- docker compose exec kafka kafka-console-consumer \
  --bootstrap-server kafka:29092 \
  --topic chat-messages \
  --from-beginning \
  --max-messages 5
```
10.14 Verify Redis presence keys
```bash
- docker compose exec redis redis-cli KEYS 'presence:*'
```

Expected:

- presence:tenant-a:user-1
10.15 Verify ClickHouse databases
```bash
- docker compose exec clickhouse clickhouse-client --query "SHOW DATABASES;"
```
10.16 Verify ClickHouse tables
```bash
- docker compose exec clickhouse clickhouse-client --query "SHOW TABLES FROM chat_analytics;"
```
10.17 Query ClickHouse analytics table
```bash
- docker compose exec clickhouse clickhouse-client --query "

- SELECT
- tenant_id,
- room_id,
- sender_id,
- count()
- FROM chat_analytics.message_events
- GROUP BY tenant_id, room_id, sender_id
- ORDER BY count() DESC;
"
```
10.18 Room analytics API
```bash
- curl http://localhost:8080/analytics/rooms/chat-1 \
  -H "X-Tenant-ID: tenant-a"
```

Expected:

```json
{
  "tenantId": "tenant-a",
  "roomId": "chat-1",
  "messageCount": 11
}
```
10.19 Top users API
```bash
- curl http://localhost:8080/analytics/top-users \
  -H "X-Tenant-ID: tenant-a"
```

Expected:

```json
[
  {
    "tenantId": "tenant-a",
    "senderId": "user-1",
    "messageCount": 11
  }
]
```

## 11. Advanced Features and Production Enhancements
11.1 Cassandra Message Storage
- What it does

- The system stores chat messages in Cassandra using a room-based query model.

- Why it matters
- Chat systems are write-heavy
- Message reads are usually scoped by room/chat
- Cassandra handles high write throughput
- Cassandra scales horizontally
- Partitioning by room allows efficient recent message retrieval
- Implementation

Messages are stored in a table similar to:

- CREATE TABLE chat.messages_by_room (
- tenant_id text,
- room_id text,
- created_at timestamp,
- message_id text,
- sender_id text,
- content text,
- PRIMARY KEY ((tenant_id, room_id), created_at, message_id)
) WITH CLUSTERING ORDER BY (created_at DESC);
- Expected query pattern
- SELECT *
- FROM chat.messages_by_room
- WHERE tenant_id = 'tenant-a'
- AND room_id = 'chat-1'
- LIMIT 10;
- Trade-offs
- Very efficient for room history queries
- Not efficient for global search
- Requires query-driven schema design
- Large rooms can create hot partitions
- Production improvement
- Add time bucket to partition key
- Use (tenant_id, room_id, yyyy_mm) for very large rooms
- Apply TTL for ephemeral messages if product allows
- Store attachments in object storage, not Cassandra

11.2 Kafka Message Event Streaming
- What it does

- Every accepted message can be published to Kafka.

- Why it matters

Kafka decouples the chat write path from downstream workflows:

- analytics
- notifications
- moderation
- search indexing
- audit pipeline
- fraud/spam detection
- Implementation

Topic:

- chat-messages

Event example:

{
  "tenantId": "tenant-a",
  "roomId": "chat-1",
  "messageId": "uuid",
  "senderId": "user-1",
  "content": "Hello world!",
  "createdAt": "2026-04-30T06:32:04.242118976Z"
}
- Trade-offs
- Adds operational complexity
- Requires idempotent consumers
- Requires monitoring consumer lag
- Eventual consistency for downstream systems
- Production improvement
- Use outbox pattern for reliable event publishing
- Add dead-letter topic
- Use schema registry
- Add event version field
- Partition by room ID to preserve room-level ordering
11.3 Redis Presence
- What it does

- Redis stores current user presence.

- Why it matters

- Presence changes frequently and should be fast.

Redis is appropriate because:

- online/offline state is short-lived
- updates are frequent
- reads need low latency
- TTL-based expiry avoids stale presence
- Implementation

Example keys:

- presence:tenant-a:user-1 -> ONLINE
- presence:tenant-b:user-1 -> OFFLINE
- Trade-offs
- Redis state is not durable by default
- temporary loss may reset presence state
- multi-device presence requires richer modeling
- Production improvement
- Use TTL heartbeat model
- Store device/session-specific presence
- Track last seen timestamp
- Use Redis Cluster for scale
- Publish presence events over WebSocket
11.4 ClickHouse OLAP Analytics
- What it does

- ClickHouse stores message analytics events.

- Why it matters

Chat analytics queries are aggregation-heavy:

- messages per room
- active users
- messages per minute
- tenant-level traffic
- channel/room activity

- Running these on Cassandra would be inefficient.

- Implementation

Table:

- chat_analytics.message_events

Example query:

- SELECT
- room_id,
- count()
- FROM chat_analytics.message_events
- WHERE tenant_id = 'tenant-a'
- GROUP BY room_id;
- Trade-offs
- Analytics are eventually consistent
- Requires separate schema management
- Requires data retention planning
- Duplicate events require idempotency handling
- Production improvement
- Use MergeTree partitioning by date
- Add materialized views
- Add rollup tables
- Use TTL for raw events
- Store aggregate tables for dashboards
11.5 Real-Time WebSocket Delivery
- What it is

- The current prototype supports REST APIs. A production chat system usually adds WebSocket delivery.

- Flow
- Client connects over WebSocket
- Connection Gateway authenticates user
- User joins rooms
- Message arrives through API or socket
- Message written to Cassandra
- Kafka event published
- Fanout service sends message to online users
- Offline users receive push notifications
- Why it matters
- REST is fine for persistence demo
- WebSocket is needed for real-time user experience
- Fanout must be separated from persistence
- Presence and connection state become first-class systems
- Production improvement
- Add WebSocket Gateway
- Store connection routing in Redis
- Use Kafka/NATS for fanout
- Use backpressure for slow clients
- Add reconnect and missed-message recovery
11.6 Cost Optimization Strategy
- Principle

- Scale each subsystem independently based on its workload.

- API Layer
- Stateless
- Horizontal scaling
- Auto-scale on CPU, RPS, and latency
- Keep only API port exposed
- Use spot instances for non-critical workers
- Cassandra
- Use proper partition keys
- Avoid unbounded partitions
- Use TTL for short-lived messages
- Use compression
- Use compaction strategy appropriate to workload
- Keep large blobs out of Cassandra
- Kafka
- Partition by room ID or tenant ID
- Avoid excessive partitions
- Tune retention based on replay needs
- Use compression
- Use batching
- Use managed Kafka only when operationally justified
- Redis
- Use TTL for presence
- Avoid storing large payloads
- Use Redis Cluster only when needed
- Keep Redis for hot state only
- ClickHouse
- Use partitioning by date
- Use TTL for raw events
- Use materialized views for common dashboards
- Store aggregates for high-cardinality dashboards
- Compress older partitions
- Avoid querying raw events for every dashboard refresh
- Cloud
- Reserved instances for steady-state baseline
- Spot instances for stateless workers
- Autoscaling groups
- Separate dev/staging/prod resource sizing
- Cold storage for old analytics
- Log retention policies
11.7 Rate Limiting
- What it does

- Prevents tenant or user abuse.

- Implementation

Redis counters:

- rate:tenant-a:messages:202604301201
- rate:tenant-a:user-1:messages:202604301201
- Why it matters
- Protects Cassandra write path
- Protects Kafka brokers
- Prevents abusive users
- Enables fair tenant usage
- Behavior
- high load triggers throttling
- system remains stable
- legitimate tenants are isolated from noisy neighbors
11.8 Cache Strategy
- Current strategy
- Redis for presence
- Cassandra for message history
- ClickHouse for analytics
- Why not cache everything

- Message history is append-heavy and can change rapidly.

Caching message history can introduce:

- invalidation complexity
- ordering issues
- stale reads
- high memory cost
- Improvements
- Cache only hot room first page
- Use short TTL
- Cache room metadata separately
- Use CDN only for static attachments
- Use client-side local cache for recent messages
11.9 Future Enhancements
- Reliability
- Transactional outbox
- Kafka DLQ
- Idempotent consumers
- Retry with backoff
- Poison event handling
- Observability
- Prometheus metrics
- Grafana dashboards
- Distributed tracing
- Kafka lag monitoring
- Cassandra partition size monitoring
- ClickHouse query latency dashboard
- Security
- JWT authentication
- Tenant authorization
- Room membership authorization
- Message encryption at rest
- TLS in transit
- PII redaction in logs
- Data correctness
- Outbox pattern
- Idempotency keys
- Event schema versioning
- De-duplication in analytics pipeline
- Product features
- WebSocket delivery
- Typing indicators
- Read receipts
- Reactions
- Attachments
- Message edits/deletes
- Search integration
- Moderation pipeline

## 12. Production Readiness Analysis
12.1 Scalability
- API layer

- The API is stateless.

Scale horizontally using:

- multiple Spring Boot instances behind a load balancer

Scale signals:

- CPU usage
- request latency
- p95/p99 latency
- message send rate
- Cassandra write latency
- Kafka publish latency
- Redis latency
- ClickHouse query latency
- Cassandra scalability

- Cassandra scales by adding nodes.

Key considerations:

- partition by tenant and room
- avoid hot rooms becoming hot partitions
- use time buckets for large rooms
- monitor partition size
- tune compaction strategy
- choose replication factor based on availability needs
- Kafka scalability

- Kafka scales with partitions and consumer groups.

Key considerations:

- partition by room ID for ordering
- partition by tenant ID for tenant isolation
- consumer lag monitoring
- idempotent producer
- retry and DLQ topics
- Redis scalability

Redis presence can scale using:

- TTL keys
- Redis Cluster
- sharded presence keys
- local cache for gateway nodes
- ClickHouse scalability

ClickHouse scales analytics using:

- MergeTree tables
- date partitioning
- materialized views
- distributed tables
- aggregate rollups
12.2 Resilience
- Cassandra failure

Impact:

- message writes may fail
- history reads may fail

Mitigation:

- multi-node Cassandra cluster
- replication factor > 1
- local quorum consistency
- retry with backoff
- circuit breaker
- queue writes temporarily if product allows
- Kafka failure

Impact:

- message can still be written to Cassandra
- downstream analytics may lag

Mitigation:

- transactional outbox
- retry publisher
- DLQ topic
- monitor broker health
- replay events after recovery
- Redis failure

Impact:

- presence may be unavailable
- chat messages can still work

Mitigation:

- degrade presence to UNKNOWN
- Redis Cluster
- TTL heartbeat model
- avoid blocking message send on Redis
- ClickHouse failure

Impact:

- analytics APIs may fail
- chat messages should still work

Mitigation:

- do not block message send on ClickHouse
- buffer analytics events in Kafka
- retry consumer
- show stale analytics if needed
12.3 Security

Production system should add:

- JWT authentication
- tenant authorization
- room membership authorization
- RBAC for admin APIs
- encryption in transit
- encryption at rest
- secret management
- PII masking in logs
- audit logs
- rate limiting
- abuse detection
- message moderation pipeline

Sensitive fields:

- message content
- sender ID
- room ID
- metadata
- attachments

- These should not be logged in raw form in production.

12.4 Observability

Key metrics:

- chat_messages_sent_total
- chat_message_send_latency_ms
- chat_message_history_latency_ms
- cassandra_write_latency_ms
- cassandra_read_latency_ms
- kafka_publish_failures_total
- kafka_consumer_lag
- redis_presence_latency_ms
- clickhouse_query_latency_ms
- analytics_event_lag_seconds

Dashboards:

- message send rate by tenant
- p95/p99 send latency
- Cassandra latency
- Kafka topic lag
- Redis latency
- ClickHouse query latency
- top rooms by traffic
- error rate by endpoint
- tenant-level traffic

Tracing:

- API request -> Cassandra write -> Kafka publish -> ClickHouse analytics

Logs should include:

- tenantId
- roomId
- messageId
- senderId
- correlationId
- status
- latency
12.5 Performance

Key optimizations:

- Cassandra append-optimized schema
- partition by tenant and room
- use clustering by timestamp
- Redis for presence
- Kafka for asynchronous downstream processing
- ClickHouse for analytics
- avoid synchronous analytics writes in hot path
- use batching for Kafka producers
- use connection pooling
- avoid storing large attachments in Cassandra

Recommended Cassandra schema:

- CREATE TABLE chat.messages_by_room (
- tenant_id text,
- room_id text,
- created_at timestamp,
- message_id text,
- sender_id text,
- content text,
- PRIMARY KEY ((tenant_id, room_id), created_at, message_id)
) WITH CLUSTERING ORDER BY (created_at DESC);

Recommended ClickHouse table:

- CREATE TABLE chat_analytics.message_events (
- tenant_id String,
- room_id String,
- message_id String,
- sender_id String,
- created_at DateTime64(3)
)
- ENGINE = MergeTree()
- PARTITION BY toYYYYMM(created_at)
- ORDER BY (tenant_id, room_id, created_at);
12.6 Operations

Deployment strategy:

- Docker for local
- Kubernetes for production
- Helm chart
- readiness/liveness probes
- rolling deployment
- blue-green deployment
- canary deployment

Operational jobs:

- Cassandra repair
- ClickHouse retention cleanup
- Kafka lag monitor
- dead-letter reprocessor
- hot partition detector
- analytics backfill job

Rollback strategy:

- keep previous app version
- schema backward compatibility
- Kafka event schema versioning
- feature flags around new consumers
- canary rollout for message delivery changes
12.7 SLA Considerations

Target SLA:

99.95% API availability

Messaging SLO examples:

99% message sends accepted within 100 ms
99.9% message history reads under 200 ms
- Kafka event lag under 10 seconds
- Presence reads under 20 ms
- Analytics freshness under 60 seconds

To achieve:

- multi-AZ deployment
- replicated Cassandra
- replicated Kafka
- Redis HA
- ClickHouse replicas
- graceful degradation
- autoscaling API workers
- alerting on SLO burn rate

## 13. Enterprise Experience Showcase

This system demonstrates enterprise-grade backend design because it:

- uses query-driven Cassandra modeling
- separates hot message path from analytics path
- uses Kafka for decoupling and replay
- uses Redis for low-latency presence
- uses ClickHouse for OLAP analytics
- supports tenant isolation
- avoids coupling message send latency to analytics
- includes clear cost optimization paths
- includes production-readiness thinking
- maps directly to common system design interview topics
