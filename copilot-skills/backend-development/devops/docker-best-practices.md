# Docker 最佳实践 Skill

> Docker 镜像构建和容器部署最佳实践

## 触发条件

- 命令: `/docker-check`
- 文件变更: Dockerfile, docker-compose.yml

---

## Dockerfile 最佳实践

### 多阶段构建

```dockerfile
# 构建阶段
FROM eclipse-temurin:17-jdk-alpine AS builder

WORKDIR /app

# 复制 Maven 配置
COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .

# 下载依赖（利用缓存）
RUN ./mvnw dependency:go-offline -B

# 复制源代码
COPY src ./src

# 构建应用
RUN ./mvnw clean package -DskipTests -B

# 运行阶段
FROM eclipse-temurin:17-jre-alpine

# 安装必要工具
RUN apk add --no-cache curl tzdata && \
    rm -rf /var/cache/apk/*

# 创建非 root 用户
RUN addgroup -S appgroup && \
    adduser -S appuser -G appgroup

WORKDIR /app

# 从构建阶段复制 JAR
COPY --from=builder /app/target/*.jar app.jar

# 设置时区
ENV TZ=Asia/Shanghai

# 切换到非 root 用户
USER appuser

# 暴露端口
EXPOSE 8080

# 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# 启动参数
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
# 构建阶段
FROM python:3.11-slim AS builder

WORKDIR /app

# 安装编译依赖
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    && rm -rf /var/lib/apt/lists/*

# 创建虚拟环境
RUN python -m venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH"

# 安装依赖
COPY requirements.txt .
RUN pip install --no-cache-dir -U pip && \
    pip install --no-cache-dir -r requirements.txt

# 运行阶段
FROM python:3.11-slim

WORKDIR /app

# 安装运行时依赖
RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    && rm -rf /var/lib/apt/lists/*

# 复制虚拟环境
COPY --from=builder /opt/venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH"

# 创建非 root 用户
RUN groupadd -r appuser && useradd -r -g appuser appuser

# 复制应用代码
COPY --chown=appuser:appuser . .

USER appuser

EXPOSE 8000

HEALTHCHECK --interval=30s --timeout=3s --start-period=30s \
    CMD curl -f http://localhost:8000/health || exit 1

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
```

### Node.js Dockerfile

```dockerfile
# 构建阶段
FROM node:20-alpine AS builder

WORKDIR /app

# 复制 package 文件
COPY package*.json ./

# 安装依赖
RUN npm ci --only=production

# 复制源代码
COPY . .

# 构建
RUN npm run build

# 运行阶段
FROM node:20-alpine

WORKDIR /app

# 创建非 root 用户
RUN addgroup -g 1001 -S nodejs && \
    adduser -S nestjs -u 1001

# 复制构建产物和依赖
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

## Docker Compose 最佳实践

### 开发环境配置

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

  # 可选: 管理工具
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

### 生产环境配置

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

## 安全最佳实践

### 1. 使用非 root 用户

```dockerfile
# 创建专用用户
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser
```

### 2. 最小化镜像

```dockerfile
# 使用 alpine 基础镜像
FROM eclipse-temurin:17-jre-alpine

# 清理缓存
RUN apk add --no-cache curl && \
    rm -rf /var/cache/apk/*
```

### 3. 不暴露敏感信息

```dockerfile
# 错误: 硬编码密码
ENV DB_PASSWORD=mypassword

# 正确: 使用环境变量或 secrets
# docker run -e DB_PASSWORD=xxx ...
# 或使用 Docker secrets
```

### 4. 扫描漏洞

```bash
# 使用 Trivy 扫描
trivy image myapp:latest

# 使用 Docker Scout
docker scout quickview myapp:latest
```

---

## .dockerignore 文件

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

## 健康检查配置

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
      show-details: when-authorized
      probes:
        enabled: true
  health:
    livenessstate:
      enabled: true
    readinessstate:
      enabled: true
```

```dockerfile
# 使用 Actuator 健康检查
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health/liveness || exit 1
```

### Kubernetes 探针

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

## 检查清单

### Dockerfile

- [ ] 使用多阶段构建
- [ ] 使用非 root 用户
- [ ] 最小化基础镜像
- [ ] 配置健康检查
- [ ] 正确的 ENTRYPOINT/CMD
- [ ] 合理的资源限制

### 安全

- [ ] 不硬编码敏感信息
- [ ] 使用 .dockerignore
- [ ] 定期扫描漏洞
- [ ] 使用签名镜像
- [ ] 最小权限原则

### 性能

- [ ] 利用构建缓存
- [ ] 合并 RUN 指令
- [ ] 减少镜像层数
- [ ] 配置 JVM 容器感知

### Docker Compose

- [ ] 使用健康检查依赖
- [ ] 配置资源限制
- [ ] 使用命名网络
- [ ] 持久化数据卷
- [ ] 环境变量外部化
