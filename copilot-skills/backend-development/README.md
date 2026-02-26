# 后端开发 Skill 索引

> GitHub Copilot / Claude Code 后端开发最佳实践 Skill 集合

## 目录结构

```
copilot-skills/
└── backend-development/
    ├── code-style/              # 代码风格
    │   ├── java-spring-boot.md
    │   ├── python-fastapi.md
    │   └── go-backend.md
    ├── feature-development/     # 功能开发
    │   ├── crud-generator.md
    │   └── restful-api-design.md
    ├── testing/                 # 测试
    │   ├── unit-test-generation.md
    │   └── integration-test-generation.md
    ├── security/                # 安全
    │   ├── owasp-security-check.md
    │   └── sql-injection-prevention.md
    ├── code-review/             # 代码审查
    │   └── pr-code-review.md
    ├── database/                # 数据库
    │   └── query-optimization.md
    ├── documentation/           # 文档
    │   └── api-documentation.md
    └── devops/                  # DevOps
        └── docker-best-practices.md
```

---

## Skill 使用指南

### 如何使用这些 Skill

#### 1. 在 Claude Code 中使用

将 `.md` 文件放入项目或全局的 skills 目录：

```
# 项目级别
.claude/skills/backend-development/

# 全局级别
~/.claude/skills/backend-development/
```

Claude Code 会自动加载并参考这些 skill。

#### 2. 在 GitHub Copilot 中使用

将内容复制到 `.github/copilot-instructions.md` 或作为对话参考。

#### 3. 作为 Prompt 参考

在 AI 对话中直接引用相关 skill 的内容作为上下文。

---

## Skill 详解

### 代码风格 (Code Style)

| Skill | 触发命令 | 说明 |
|-------|----------|------|
| [Java Spring Boot](./code-style/java-spring-boot.md) | `/java-style` | Java Spring Boot 代码规范、命名、注解、异常处理 |
| [Python FastAPI](./code-style/python-fastapi.md) | `/python-style` | Python 代码规范、类型注解、FastAPI 最佳实践 |
| [Go Backend](./code-style/go-backend.md) | `/go-style` | Go 代码规范、错误处理、并发模式 |

### 功能开发 (Feature Development)

| Skill | 触发命令 | 说明 |
|-------|----------|------|
| [CRUD 生成器](./feature-development/crud-generator.md) | `/generate-crud` | 自动生成完整的 CRUD 代码模板 |
| [RESTful API 设计](./feature-development/restful-api-design.md) | `/api-design` | RESTful API 设计规范和最佳实践 |

### 测试 (Testing)

| Skill | 触发命令 | 说明 |
|-------|----------|------|
| [单元测试生成](./testing/unit-test-generation.md) | `/gen-unit-tests` | 生成 JUnit 5 + Mockito 单元测试 |
| [集成测试生成](./testing/integration-test-generation.md) | `/gen-integration-tests` | 生成 Spring Boot 集成测试 |

### 安全 (Security)

| Skill | 触发命令 | 说明 |
|-------|----------|------|
| [OWASP 安全检查](./security/owasp-security-check.md) | `/security-scan` | OWASP Top 10 安全检查清单 |
| [SQL 注入防护](./security/sql-injection-prevention.md) | `/sql-injection-check` | SQL 注入漏洞检测和修复 |

### 代码审查 (Code Review)

| Skill | 触发命令 | 说明 |
|-------|----------|------|
| [PR 代码审查](./code-review/pr-code-review.md) | 自动触发 | Pull Request 代码审查规则和报告模板 |

### 数据库 (Database)

| Skill | 触发命令 | 说明 |
|-------|----------|------|
| [查询优化](./database/query-optimization.md) | `/query-optimize` | N+1 查询、索引优化、分页优化 |

### 文档 (Documentation)

| Skill | 触发命令 | 说明 |
|-------|----------|------|
| [API 文档生成](./documentation/api-documentation.md) | `/gen-api-docs` | OpenAPI/Swagger 文档注解规范 |

### DevOps

| Skill | 触发命令 | 说明 |
|-------|----------|------|
| [Docker 最佳实践](./devops/docker-best-practices.md) | `/docker-check` | Dockerfile、Docker Compose 最佳实践 |

---

## 快速参考

### Java Spring Boot 分层架构

```
Controller → Service → Repository → Entity
    ↓          ↓
   DTO      Domain Events
```

### 常用注解速查

| 层级 | 核心注解 |
|------|----------|
| Controller | @RestController, @RequestMapping, @GetMapping |
| Service | @Service, @Transactional |
| Repository | @Repository, @Query |
| Entity | @Entity, @Table, @Column |
| DTO | @Data, @Builder, @Valid |

### 安全检查清单

- [ ] 认证授权完整
- [ ] 参数化查询
- [ ] 输入验证
- [ ] 敏感信息加密
- [ ] 错误信息脱敏

### 性能优化清单

- [ ] 避免 N+1 查询
- [ ] 合理使用索引
- [ ] 分页查询优化
- [ ] 缓存热点数据

---

## 版本信息

- **版本**: 1.0.0
- **更新日期**: 2026-02-24
- **适用框架**: Spring Boot 3.x, FastAPI 0.100+, Go 1.21+

---

## 贡献指南

欢迎贡献新的 Skill 或改进现有 Skill：

1. Fork 仓库
2. 创建新的 Skill 文件（Markdown 格式）
3. 遵循现有 Skill 的格式规范
4. 提交 Pull Request

### Skill 格式规范

```markdown
# Skill 标题

> 简短描述

## 触发条件
- 命令
- 文件变更

## 内容
- 代码示例
- 最佳实践
- 检查清单
```
