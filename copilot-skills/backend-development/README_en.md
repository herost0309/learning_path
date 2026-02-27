# Backend Development Skill Index

> GitHub Copilot / Claude Code Backend Development Best Practices Skill Collection

## Directory Structure

```
copilot-skills/
└── backend-development/
    ├── code-style/              # Code Style
    │   ├── java-spring-boot.md
    │   ├── python-fastapi.md
    │   └── go-backend.md
    ├── feature-development/     # Feature Development
    │   ├── crud-generator.md
    │   └── restful-api-design.md
    ├── testing/                 # Testing
    │   ├── unit-test-generation.md
    │   └── integration-test-generation.md
    ├── security/                # Security
    │   ├── owasp-security-check.md
    │   └── sql-injection-prevention.md
    ├── code-review/             # Code Review
    │   └── pr-code-review.md
    ├── database/                # Database
    │   └── query-optimization.md
    ├── documentation/           # Documentation
    │   └── api-documentation.md
    └── devops/                  # DevOps
        └── docker-best-practices.md
```

---

## Skill Usage Guide

### How to Use These Skills

#### 1. Using in Claude Code

Place the `.md` files in the project or global skills directory:

```
# Project level
.claude/skills/backend-development/

# Global level
~/.claude/skills/backend-development/
```

Claude Code will automatically load and reference these skills.

#### 2. Using in GitHub Copilot

Copy the content to `.github/copilot-instructions.md` or use as conversation reference.

#### 3. As Prompt Reference

Directly reference relevant skill content as context in AI conversations.

---

## Skill Details

### Code Style

| Skill | Trigger Command | Description |
|-------|-----------------|-------------|
| [Java Spring Boot](./code-style/java-spring-boot.md) | `/java-style` | Java Spring Boot code standards, naming, annotations, exception handling |
| [Python FastAPI](./code-style/python-fastapi.md) | `/python-style` | Python code standards, type annotations, FastAPI best practices |
| [Go Backend](./code-style/go-backend.md) | `/go-style` | Go code standards, error handling, concurrency patterns |

### Feature Development

| Skill | Trigger Command | Description |
|-------|-----------------|-------------|
| [CRUD Generator](./feature-development/crud-generator.md) | `/generate-crud` | Automatically generate complete CRUD code templates |
| [RESTful API Design](./feature-development/restful-api-design.md) | `/api-design` | RESTful API design standards and best practices |

### Testing

| Skill | Trigger Command | Description |
|-------|-----------------|-------------|
| [Unit Test Generation](./testing/unit-test-generation.md) | `/gen-unit-tests` | Generate JUnit 5 + Mockito unit tests |
| [Integration Test Generation](./testing/integration-test-generation.md) | `/gen-integration-tests` | Generate Spring Boot integration tests |

### Security

| Skill | Trigger Command | Description |
|-------|-----------------|-------------|
| [OWASP Security Check](./security/owasp-security-check.md) | `/security-scan` | OWASP Top 10 security checklist |
| [SQL Injection Prevention](./security/sql-injection-prevention.md) | `/sql-injection-check` | SQL injection vulnerability detection and fixes |

### Code Review

| Skill | Trigger Command | Description |
|-------|-----------------|-------------|
| [PR Code Review](./code-review/pr-code-review.md) | Auto-trigger | Pull Request code review rules and report templates |

### Database

| Skill | Trigger Command | Description |
|-------|-----------------|-------------|
| [Query Optimization](./database/query-optimization.md) | `/query-optimize` | N+1 queries, index optimization, pagination optimization |

### Documentation

| Skill | Trigger Command | Description |
|-------|-----------------|-------------|
| [API Documentation Generation](./documentation/api-documentation.md) | `/gen-api-docs` | OpenAPI/Swagger documentation annotation standards |

### DevOps

| Skill | Trigger Command | Description |
|-------|-----------------|-------------|
| [Docker Best Practices](./devops/docker-best-practices.md) | `/docker-check` | Dockerfile, Docker Compose best practices |

---

## Quick Reference

### Java Spring Boot Layered Architecture

```
Controller → Service → Repository → Entity
    ↓          ↓
   DTO      Domain Events
```

### Common Annotations Quick Reference

| Layer | Core Annotations |
|-------|-----------------|
| Controller | @RestController, @RequestMapping, @GetMapping |
| Service | @Service, @Transactional |
| Repository | @Repository, @Query |
| Entity | @Entity, @Table, @Column |
| DTO | @Data, @Builder, @Valid |

### Security Checklist

- [ ] Complete authentication and authorization
- [ ] Parameterized queries
- [ ] Input validation
- [ ] Sensitive information encryption
- [ ] Error message sanitization

### Performance Optimization Checklist

- [ ] Avoid N+1 queries
- [ ] Reasonable index usage
- [ ] Pagination query optimization
- [ ] Cache hot data

---

## Version Information

- **Version**: 1.0.0
- **Updated**: 2026-02-24
- **Supported Frameworks**: Spring Boot 3.x, FastAPI 0.100+, Go 1.21+

---

## Contributing Guide

Contributions of new Skills or improvements to existing Skills are welcome:

1. Fork the repository
2. Create new Skill files (Markdown format)
3. Follow existing Skill format standards
4. Submit a Pull Request

### Skill Format Standards

```markdown
# Skill Title

> Brief description

## Trigger Conditions
- Commands
- File changes

## Content
- Code examples
- Best practices
- Checklists
```
