# Docker Best Practices Skill

> Docker image building and container deployment best practices

## Trigger Conditions

- Command: `/docker-check`
- File Changes: Dockerfile, docker-compose.yml

---

## Dockerfile Best Practices

### Multi-stage Build

```dockerfile
# Build stage
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

# Copy Maven configuration
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .

# Download dependencies (utilize cache)
RUN ./mvnw dependency:go-offline -B

# Copy source code
COPY src ./src

# Build application
RUN ./mvnw clean package -DskipTests -B

# Runtime stage
FROM eclipse-temurin:17-jre-alpine

# Install necessary tools
RUN apk add --no-cache curl tzdata && \
    rm -rf /var/cache/apk/*

# Create non-root user
RUN addgroup -S appgroup && \
    adduser -S appuser -G appgroup

WORKDIR /app

# Copy JAR from build stage
COPY --from=builder /app/target/*.jar app.jar

# Set timezone
ENV TZ=Asia/Shanghai

# Switch to non-root user
USER appuser

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# Startup parameters
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-XX:InitialRAMPercentage=50.0", \
    "-XX:+UseG1GC", \
    "-XX:MaxGCPauseMillis=200", \
    "-XX:+HeapDumpOnOutOfMemoryError", \
    "-XX:HeapDumpPath=/app/logs", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-Dspring.profiles.active=${SPRING_PROFILES_ACTIVE:-prod}", \
    "-jar", "app.jar"]
```

### Python Dockerfile

```dockerfile
# Build stage
FROM python:3.11-slim AS builder

WORKDIR /app

# Install build dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    && rm -rf /var/lib/apt/lists/*

# Create virtual environment
RUN python -m venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH"

# Install dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -U pip && \
    pip install --no-cache-dir -r requirements.txt

# Runtime stage
FROM python:3.11-slim

WORKDIR /app

# Install runtime dependencies
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Copy virtual environment
COPY --from=builder /opt/venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH"

# Create non-root user
RUN groupadd -r appuser && useradd -r -g appuser appuser

# Copy application code
COPY --chown=appuser:appuser . .

USER appuser

EXPOSE 8000

HEALTHCHECK --interval=30s --timeout=3s --start-period=30s \
    CMD curl -f http://localhost:8000/health || exit 1

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
```

### Node.js Dockerfile

```dockerfile
# Build stage
FROM node:20-alpine AS builder

WORKDIR /app

# Copy package files
COPY package*.json ./

# Install dependencies
RUN npm ci --only=production

# Copy source code
COPY . .

# Build
RUN npm run build

# Runtime stage
FROM node:20-alpine

WORKDIR /app

# Create non-root user
RUN addgroup -g 1001 -S nodejs && \
    adduser -S nestjs -u 1001

# Copy build artifacts and dependencies
COPY --from=builder --chown=nestjs:nodejs /app/dist ./dist
COPY --from=builder --chown=nestjs:nodejs /app/node_modules ./node_modules
COPY --from=builder --chown=nestjs:nodejs /app/package.json ./

USER nestjs

EXPOSE 3000

HEALTHCHECK --interval=30s --timeout=3s \
    CMD wget --no-verbose --tries=1 --spider http://localhost:3000/health || exit 1

CMD ["node", "dist/main.js"]
```

---

## Docker Compose Best Practices

### Development Environment Configuration

```yaml
# docker-compose.yml
version: '3.8'

services:
  app:
    build:
      context: .
      dockerfile: Dockerfile
      target: development
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=dev
      - SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/myapp
      - SPRING_DATASOURCE_USERNAME=myapp
      - SPRING_DATASOURCE_PASSWORD=myapp123
      - SPRING_REDIS_HOST=redis
    volumes:
      - .:/app
      - ~/.m2:/root/.m2
    depends_on:
      db:
        condition: service_healthy
      redis:
        condition: service_started
    networks:
      - app-network

  db:
    image: postgres:15-alpine
    environment:
      - POSTGRES_DB=myapp
      - POSTGRES_USER=myapp
      - POSTGRES_PASSWORD=myapp123
    volumes:
      - postgres-data:/var/lib/postgresql/data
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U myapp"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - app-network

  redis:
    image: redis:7-alpine
    command: redis-server --appendonly yes
    volumes:
      - redis-data:/data
    ports:
      - "6379:6379"
    networks:
      - app-network

  # Optional: Admin tools
  adminer:
    image: adminer
    ports:
      - "8081:8080"
    networks:
      - app-network

networks:
  app-network:
    driver: bridge

volumes:
  postgres-data:
  redis-data:
```

### Production Environment Configuration

```yaml
# docker-compose.prod.yml
version: '3.8'

services:
  app:
    image: ${DOCKER_REGISTRY}/myapp:${APP_VERSION}
    ports:
      - "8080:8080"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - SPRING_DATASOURCE_URL=${DB_URL}
      - SPRING_DATASOURCE_USERNAME=${DB_USERNAME}
      - SPRING_DATASOURCE_PASSWORD=${DB_PASSWORD}
      - JAVA_OPTS=-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0
    deploy:
      replicas: 3
      resources:
        limits:
          cpus: '2'
          memory: 2G
        reservations:
          cpus: '0.5'
          memory: 512M
      restart_policy:
        condition: on-failure
        delay: 5s
        max_attempts: 3
      update_config:
        parallelism: 1
        delay: 10s
        failure_action: rollback
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 3s
      retries: 3
      start_period: 60s
    logging:
      driver: "json-file"
      options:
        max-size: "10m"
        max-file: "3"
    networks:
      - app-network

networks:
  app-network:
    external: true
```

---

## Security Best Practices

### 1. Use Non-root User

```dockerfile
# Create dedicated user
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser
```

### 2. Minimize Image

```dockerfile
# Use alpine base image
FROM eclipse-temurin:17-jre-alpine

# Clean cache
RUN apk add --no-cache curl && \
    rm -rf /var/cache/apk/*
```

### 3. Don't Expose Sensitive Information

```dockerfile
# Wrong: Hardcoded password
ENV DB_PASSWORD=mypassword

# Correct: Use environment variables or secrets
# docker run -e DB_PASSWORD=xxx ...
# Or use Docker secrets
```

### 4. Scan for Vulnerabilities

```bash
# Use Trivy to scan
trivy image myapp:latest

# Use Docker Scout
docker scout quickview myapp:latest
```

---

## .dockerignore File

```text
# Git
.git
.gitignore

# IDE
.idea
.vscode
*.iml

# Build
target/
build/
dist/
node_modules/

# Logs
*.log
logs/

# Test
test/
tests/
*.test.js
*_test.go

# Documentation
docs/
*.md
!README.md

# Environment
.env
.env.*
!.env.example

# Docker
Dockerfile*
docker-compose*.yml
.docker/

# CI/CD
.github/
.gitlab-ci.yml
Jenkinsfile

# Other
*.swp
*.swo
*~
.DS_Store
```

---

## Health Check Configuration

### Spring Boot Actuator

```yaml
# application.yml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: when_authorized
      probes:
        enabled: true
  health:
    livenessstate:
      enabled: true
    readinessstate:
      enabled: true
```

```dockerfile
# Use Actuator health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health/liveness || exit 1
```

### Kubernetes Probes

```yaml
# deployment.yaml
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 60
  periodSeconds: 10
  timeoutSeconds: 3
  failureThreshold: 3

readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 5
  timeoutSeconds: 3
  failureThreshold: 3

startupProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 10
  periodSeconds: 10
  timeoutSeconds: 3
  failureThreshold: 30
```

---

## Checklist

### Dockerfile

- [ ] Use multi-stage builds
- [ ] Use non-root user
- [ ] Minimize base image
- [ ] Configure health check
- [ ] Correct ENTRYPOINT/CMD
- [ ] Reasonable resource limits

### Security

- [ ] No hardcoded sensitive information
- [ ] Use .dockerignore
- [ ] Regularly scan for vulnerabilities
- [ ] Use signed images
- [ ] Principle of least privilege

### Performance

- [ ] Utilize build cache
- [ ] Merge RUN instructions
- [ ] Reduce image layers
- [ ] Configure JVM container awareness

### Docker Compose

- [ ] Use health check dependencies
- [ ] Configure resource limits
- [ ] Use named networks
- [ ] Persist data volumes
- [ ] Externalize environment variables
