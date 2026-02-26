# AWS SQS Message Loss Prevention Demo

This project demonstrates a comprehensive implementation of AWS SQS with message loss prevention strategies using Spring Boot.

## Features

### 1. Message Persistence (Send-Before-Publish Pattern)
- Messages are persisted to the database before being sent to SQS
- Ensures no message is lost even if SQS is unavailable
- Complete message lifecycle tracking (PENDING → SENT → RECEIVED → PROCESSING → PROCESSED → ACKNOWLEDGED)

### 2. Manual Acknowledge Mode
- Consumers use manual acknowledgment instead of auto-delete
- Ensures messages are only deleted after successful processing
- Visibility timeout prevents message duplication during processing

### 3. Dead Letter Queue (DLQ) Handling
- Automatic DLQ configuration for failed messages
- DLQ message tracking with retry mechanisms
- Automatic retry with exponential backoff
- Manual intervention workflow for problematic messages

### 4. Message Reconciliation
- Periodic checks for timeout messages in each state
- Automatic replay of lost messages
- Statistics and monitoring of message flow

### 5. Idempotency
- Redis-based deduplication for message processing
- Prevents duplicate processing of the same message

### 6. FIFO Queue Support
- Strict message ordering
- Content-based deduplication

## Architecture

```
┌─────────────┐
│   Producer  │
└──────┬──────┘
       │
       ▼
┌─────────────────────────────────┐
│  Message Persistence Service   │  (DB: PENDING)
└──────┬──────────────────────────┘
       │
       ▼
┌─────────────────────────────────┐
│      AWS SQS (Standard/FIFO)    │
└──────┬──────────────────────────┘
       │
       ▼
┌─────────────────────────────────┐
│     Manual Ack Consumer         │  (DB: RECEIVED → PROCESSING → PROCESSED)
└──────┬──────────────────────────┘
       │
       │ Failure (retry count > max)
       ▼
┌─────────────────────────────────┐
│       Dead Letter Queue        │  (DB: FAILED → DLQ)
└──────┬──────────────────────────┘
       │
       ▼
┌─────────────────────────────────┐
│      DLQ Handler Service        │  (Auto-retry / Manual intervention)
└─────────────────────────────────┘

┌─────────────────────────────────┐
│   Reconciliation Service        │  (Periodic timeout detection)
└─────────────────────────────────┘
```

## Message Flow

### Normal Flow
```
1. Producer creates message
2. MessagePersistenceService persists (status: PENDING)
3. MessageProducer sends to SQS (status: SENT)
4. Consumer receives from SQS (status: RECEIVED)
5. Consumer processes message (status: PROCESSING)
6. Consumer acknowledges (status: PROCESSED, then ACKNOWLEDGED)
7. SQS deletes message
```

### Failure Flow
```
1. Processing fails or times out
2. Consumer does not acknowledge
3. SQS returns message to queue after visibility timeout
4. After max retries, SQS sends to DLQ
5. DLQ consumer records to FailedMessage table
6. DLQ Handler attempts automatic retry
7. If max retry exceeded, marks for manual intervention
```

### Reconciliation Flow
```
1. Reconciliation Service runs periodically
2. Checks for timeout messages in each status
3. Identifies messages that may be lost
4. Replays lost messages by resending to SQS
5. Updates original message status to REPLAYED
```

## Project Structure

```
aws-sqs-demo/
├── src/main/java/com/example/awssqs/
│   ├── AwsSqsDemoApplication.java     # Main application class
│   ├── config/
│   │   └── SqsConfiguration.java        # SQS and AWS configuration
│   ├── domain/
│   │   ├── DomainEvent.java            # Base event class
│   │   ├── MessagePersistence.java     # Message tracking entity
│   │   └── FailedMessage.java          # DLQ message entity
│   ├── repository/
│   │   ├── MessagePersistenceRepository.java
│   │   └── FailedMessageRepository.java
│   ├── producer/
│   │   ├── MessagePersistenceService.java  # Message persistence and state management
│   │   └── MessageProducer.java            # SQS message sending
│   ├── consumer/
│   │   ├── SqsMessageConsumer.java         # SQS listeners with manual ack
│   │   └── MessageHandler.java             # Business logic processing
│   ├── dlq/
│   │   └── DeadLetterQueueService.java     # DLQ handling and retry
│   ├── reconciliation/
│   │   └── ReconciliationService.java       # Message reconciliation
│   └── controller/
│       └── SqsDemoController.java          # REST API for testing
└── src/main/resources/
    └── application.yml                     # Application configuration
```

## Prerequisites

- Java 17+
- Maven 3.6+
- MySQL 8.0+
- Redis 6.0+
- AWS Account with SQS access

## Setup

### 1. Clone and Build

```bash
cd aws-sqs-demo
mvn clean install
```

### 2. Configure Database

Create a MySQL database:

```sql
CREATE DATABASE sqs_demo;
```

Tables will be auto-created by Hibernate (ddl-auto: update).

### 3. Configure AWS Credentials

Set AWS credentials via environment variables:

```bash
export AWS_ACCESS_KEY_ID=your-access-key
export AWS_SECRET_ACCESS_KEY=your-secret-key
export AWS_REGION=us-east-1
```

Or update `application.yml`.

### 4. Configure SQS Queues

Create the following SQS queues (optional for demo):

```bash
# Standard queues
aws sqs create-queue --queue-name domain-events
aws sqs create-queue --queue-name long-task-queue
aws sqs create-queue --queue-name batch-queue
aws sqs create-queue --queue-name reliable-queue

# FIFO queue (requires .fifo suffix)
aws sqs create-queue --queue-name ticket-events.fifo --attributes FifoQueue=true

# DLQ
aws sqs create-queue --queue-name dead-letter-queue
```

Configure DLQ for main queues:

```bash
aws sqs set-queue-attributes \
  --queue-url https://sqs.us-east-1.amazonaws.com/123456789012/domain-events \
  --attributes RedrivePolicy='{"deadLetterTargetArn":"arn:aws:sqs:us-east-1:123456789012:dead-letter-queue","maxReceiveCount":3}'
```

### 5. Run Application

```bash
mvn spring-boot:run
```

Or run the JAR:

```bash
java -jar target/aws-sqs-demo-1.0.0.jar
```

## API Endpoints

### Send Messages

**Send ticket event:**
```bash
curl -X POST http://localhost:8080/api/sqs/send/ticket \
  -H "Content-Type: application/json" \
  -d '{
    "ticketId": 12345,
    "requesterId": 100,
    "organizationId": 1,
    "subject": "Test Ticket",
    "description": "This is a test ticket",
    "type": "question",
    "priority": "normal",
    "via": "web",
    "tenantId": 1
  }'
```

**Send to FIFO queue:**
```bash
curl -X POST "http://localhost:8080/api/sqs/send/fifo?messageGroupId=ticket-123" \
  -H "Content-Type: application/json" \
  -d '{
    "eventType": "TicketCreated",
    "ticketId": 12345,
    "subject": "Test Ticket"
  }'
```

**Batch send:**
```bash
curl -X POST http://localhost:8080/api/sqs/send/batch \
  -H "Content-Type: application/json" \
  -d '{
    "payloads": [
      {"id": 1, "message": "First message"},
      {"id": 2, "message": "Second message"}
    ],
    "tenantId": 1
  }'
```

**Send with delay:**
```bash
curl -X POST "http://localhost:8080/api/sqs/send/delayed?delaySeconds=60" \
  -H "Content-Type: application/json" \
  -d '{
    "id": 1,
    "message": "Delayed message"
  }'
```

### Reconciliation

**Trigger manual reconciliation:**
```bash
curl -X POST http://localhost:8080/api/sqs/reconciliation/manual
```

**Get reconciliation stats:**
```bash
curl http://localhost:8080/api/sqs/reconciliation/stats
```

### DLQ Management

**Get DLQ messages:**
```bash
curl http://localhost:8080/api/sqs/dlq/messages
```

**Handle DLQ message (retry):**
```bash
curl -X POST "http://localhost:8080/api/sqs/dlq/handle?messageId=msg-123&handledBy=admin&notes=Retry+after+fix&retry=true"
```

**Ignore DLQ message:**
```bash
curl -X POST "http://localhost:8080/api/sqs/dlq/ignore?messageId=msg-123&handledBy=admin&reason=Duplicate+message"
```

### Monitoring

**Health check:**
```bash
curl http://localhost:8080/api/sqs/health
```

**Get stats:**
```bash
curl http://localhost:8080/api/sqs/stats
```

## Configuration

Key configuration options in `application.yml`:

```yaml
sqs:
  # Queue names
  queue:
    ticket: ticket-events.fifo
    events: domain-events

  # DLQ settings
  dlq:
    name: dead-letter-queue
    max-receive-count: 3

  # Producer settings
  producer:
    enable-deduplication: true
    batch-size: 10

  # Consumer settings
  consumer:
    visibility-timeout-seconds: 30
    wait-time-seconds: 20
    max-messages-per-poll: 10
    enable-manual-ack: true

  # Reconciliation settings
  reconciliation:
    enabled: true
    timeout-minutes: 30
    batch-size: 100
    max-replay-count: 3
```

## Message States

| State        | Description                           |
|--------------|---------------------------------------|
| PENDING      | Message created, not yet sent to SQS  |
| SENT         | Successfully sent to SQS              |
| RECEIVED     | Consumer received from SQS            |
| PROCESSING   | Consumer processing message           |
| PROCESSED    | Consumer finished processing          |
| FAILED       | Processing failed                      |
| REPLAYED     | Message replayed by reconciliation    |
| LOST         | Message confirmed lost                |
| ACKNOWLEDGED | Message deleted from SQS              |

## DLQ Message States

| State    | Description                              |
|----------|------------------------------------------|
| PENDING  | In DLQ, awaiting retry                  |
| RETRYING | Currently being retried                  |
| MANUAL   | Requires manual intervention             |
| RESOLVED | Successfully resolved                   |
| IGNORED  | Ignored (no further action)             |

## Scheduled Tasks

| Task                      | Frequency | Description                       |
|---------------------------|-----------|-----------------------------------|
| DLQ Retry                 | 5 min     | Auto-retry failed messages        |
| DLQ Timeout Check        | 1 hour    | Check for timeout PENDING DLQ msgs|
| Reconciliation            | 10 min    | Check for lost messages           |

## Best Practices

1. **Always persist messages before sending** to prevent loss during network issues
2. **Use manual acknowledgment** to ensure messages are only deleted after processing
3. **Set appropriate visibility timeout** based on your processing time
4. **Configure DLQ** with reasonable max receive count (3-5 recommended)
5. **Enable message reconciliation** to detect and recover lost messages
6. **Implement idempotency** to handle duplicate message processing
7. **Monitor message queues** and set up alerts for DLQ message growth
8. **Use FIFO queues** when message ordering is critical
9. **Set message retention** appropriately (default is 4 days, max 14 days)

## Troubleshooting

### Messages not being consumed
- Check consumer logs for errors
- Verify queue URL is correct
- Ensure DLQ is not configured too aggressively
- Check visibility timeout setting

### Messages going to DLQ frequently
- Review error logs in FailedMessage table
- Check if processing time exceeds visibility timeout
- Increase max receive count if transient errors

### Reconciliation replaying too many messages
- Check timeout-minutes setting
- Review recentlyReplayed map retention
- Ensure message persistence is being updated correctly

## License

This is a demo project for educational purposes.
