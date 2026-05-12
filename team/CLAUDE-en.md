# Project Standards (CLAUDE.md)

> This file is the persistent record of team coding standards, architecture guidelines, and AI collaboration practices.
> All developers (human and AI agents) must follow these standards when modifying code in this project.

---

## 1. Project Overview

This is the main repository for [Project Name]. The technology stack is:

- **Backend**: [e.g., Java/Spring Boot, Python/Django, Node.js/Express]
- **Frontend**: [e.g., React, Vue, Angular]
- **Database**: [e.g., MySQL, PostgreSQL, MongoDB]
- **Deployment**: [e.g., Docker/K8s, cloud services]
- **CI/CD**: [e.g., GitLab CI, Jenkins]

## 2. Code Structure

```
[project-root]/
├── src/                    # Source code
│   ├── modules/            # Business modules
│   ├── common/             # Shared components/utilities
│   ├── config/             # Configuration files
│   └── tests/              # Test code
├── docs/                   # Documentation
├── scripts/                # Script tools
├── .claude/                # Claude Code configuration
│   └── skills/             # Team reusable Skills
└── CLAUDE.md               # This file
```

## 3. Coding Standards

### 3.1 General Standards

- All new code must have corresponding unit tests; coverage target >= 80%
- Functions/methods must not exceed 50 lines; refactor if longer
- Follow the project's existing naming conventions; do not mix styles
- Commit message format: `<type>: <description>`, where type includes:
  - `feat`: New feature
  - `fix`: Bug fix
  - `refactor`: Refactoring (no behavioral change)
  - `test`: Test-related
  - `docs`: Documentation
  - `chore`: Build/tooling changes

### 3.2 Backend Standards

- API endpoints must follow RESTful conventions (see Section 8)
- Error handling must use the project's unified error classes; never throw raw exceptions
- Database queries must be covered by indexes; N+1 queries must be caught during review
- New endpoints must include parameter validation and API documentation

### 3.3 Frontend Standards

- Component granularity: single file must not exceed 300 lines
- State management must use the project's chosen solution (Redux/MobX/Pinia); do not mix
- Styles must use [CSS Modules / Tailwind / styled-components] consistently
- Internationalization: all user-facing text must go through i18n

### 3.4 Security Standards

- Never hardcode secrets, passwords, or tokens; use environment variables or a secrets manager
- All external input must be validated and escaped to prevent XSS/SQL injection
- Sensitive endpoints must have authorization checks
- Dependencies must be updated regularly; CVE vulnerabilities must be addressed within 48 hours

## 4. AI Collaboration Standards

### 4.1 Spec-Driven Development (SDD) Workflow

New feature development follows the **Spec -> Agent Implementation -> Human Review** workflow:

1. **Write Spec**: Developer writes a requirements specification in natural language (stored in `docs/specs/`)
2. **Agent Implementation**: Use Claude Code or another AI agent to generate code from the Spec
3. **Human Review**: Developer reviews the agent-generated code for correctness and security
4. **Test Verification**: Run the test suite to ensure all tests pass

### 4.2 Agent Usage Principles

- **Humans decide, agents execute**: Architecture design, technology choices, and security decisions are made by humans
- **Agent-generated code must be reviewed**: AI-generated code may be functionally correct but lacks architectural judgment
- **Prefer existing Skills**: Use skills from `.claude/skills/` for repetitive tasks; create new ones when none exist
- **Keep CLAUDE.md updated**: Update this file whenever project standards change

### 4.3 Legacy Code Principles

Follow the **"Map First, Modify Later"** principle:

1. Use an agent to analyze the module and generate a module map (data flow, dependencies, risk areas)
2. Generate tests for legacy code first (safety net)
3. Refactor incrementally; run tests after each change to confirm no regression

## 5. Common Commands

```bash
# Install dependencies
[fill in project install command]

# Run development server
[fill in dev server command]

# Run tests
[fill in test command]

# Run linter
[fill in lint command]

# Build
[fill in build command]

# Deploy
[fill in deploy command]
```

## 6. Git Workflow

- **Main branch**: `main` (protected, direct push forbidden)
- **Development branch**: `develop`
- **Feature branches**: `feat/<issue-id>-<brief-description>`
- **Fix branches**: `fix/<issue-id>-<brief-description>`
- **Release branches**: `release/<version>`
- Merges use Merge Requests / Pull Requests; at least 1 reviewer approval required
- AI-generated code must be tagged `[AI-Generated]` in the MR description

## 7. Team Knowledge Base

- **Architecture docs**: `docs/architecture/`
- **API documentation**: `docs/api/`
- **Requirement specs**: `docs/specs/`
- **AI best practices**: `docs/ai-practices/`
- **Operations manual**: `docs/ops/`

## 8. RESTful API Conventions

All APIs must follow RESTful best practices:

### 8.1 URL Design

- Use nouns, not verbs: `/users` not `/getUsers`
- Use plural nouns for collections: `/users`, `/orders`
- Use kebab-case for multi-word resources: `/user-profiles`
- Nest resources to express relationships: `/users/{id}/orders`
- Limit nesting to one level deep; use query parameters for deeper relationships

### 8.2 HTTP Methods

| Method | Usage | Idempotent | Safe |
|--------|-------|------------|------|
| `GET` | Retrieve resources | Yes | Yes |
| `POST` | Create a new resource | No | No |
| `PUT` | Replace a resource entirely | Yes | No |
| `PATCH` | Partially update a resource | No | No |
| `DELETE` | Remove a resource | Yes | No |

### 8.3 HTTP Status Codes

| Code | Usage |
|------|-------|
| `200 OK` | Successful GET, PUT, PATCH, or DELETE |
| `201 Created` | Successful POST (resource created) |
| `204 No Content` | Successful DELETE or PUT with no response body |
| `400 Bad Request` | Validation error or malformed request |
| `401 Unauthorized` | Missing or invalid authentication |
| `403 Forbidden` | Authenticated but not authorized |
| `404 Not Found` | Resource does not exist |
| `409 Conflict` | Request conflicts with current state |
| `422 Unprocessable Entity` | Semantic validation errors |
| `429 Too Many Requests` | Rate limit exceeded |
| `500 Internal Server Error` | Unexpected server error |

### 8.4 Request & Response Format

- Use `Content-Type: application/json` for request and response bodies
- Use `Accept: application/json` header for API versioning when needed
- Snake_case for JSON field names: `{ "first_name": "John" }`
- Wrap collection responses with pagination metadata:

```json
{
  "data": [...],
  "pagination": {
    "page": 1,
    "per_page": 20,
    "total": 150,
    "total_pages": 8
  }
}
```

### 8.5 Error Response Format

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Human-readable error description",
    "details": [
      {
        "field": "email",
        "message": "Must be a valid email address"
      }
    ]
  }
}
```

### 8.6 Versioning

- Use URL path versioning: `/api/v1/users`
- Maintain backward compatibility within a version
- Breaking changes require a new version

### 8.7 Filtering, Sorting, and Pagination

- Filtering: query parameters `GET /users?status=active&role=admin`
- Sorting: `GET /users?sort=created_at&order=desc`
- Pagination: `GET /users?page=1&per_page=20`
- Field selection: `GET /users?fields=id,name,email`

---

## Changelog

| Date | Change | Author |
|------|--------|--------|
| 2026-05-12 | Initial version | [fill in] |

> **Note**: This file is the carrier of team consensus. Anyone (including AI agents) should read this file before modifying code.
> If standards change, update this file and notify the team.
