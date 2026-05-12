# API Spec Document Template

> Used for the Spec-Driven Development (SDD) workflow. Before developing a new feature, fill in this template, then let the Agent implement it.

---

## Basic Information

| Item | Value |
|------|-------|
| Feature name | [fill in] |
| Author | [fill in] |
| Created date | [fill in] |
| Priority | P0 / P1 / P2 |
| Estimated impact scope | [module/system] |

---

## 1. Background

[Why is this feature needed? What problem does it solve?]

## 2. Feature Description

### 2.1 User Story

As a [role], I want [feature], so that [goal].

### 2.2 Scope

**In Scope**:
- [Feature point 1]
- [Feature point 2]

**Out of Scope**:
- [Exclusion 1]
- [Exclusion 2]

### 2.3 Detailed Description

[Detailed behavioral description of the feature, including main flow and alternative flows]

## 3. API Design (RESTful)

> Follow the project's RESTful conventions defined in CLAUDE.md Section 8.

### 3.1 Endpoints

#### List Resources

```
GET /api/v1/{resources}
```

**Query Parameters**:

| Parameter | Type | Required | Default | Validation | Description |
|-----------|------|----------|---------|------------|-------------|
| page | integer | No | 1 | min: 1 | Page number |
| per_page | integer | No | 20 | min: 1, max: 100 | Items per page |
| sort | string | No | created_at | one of: [fields] | Sort field |
| order | string | No | desc | one of: asc, desc | Sort direction |
| [filter] | [type] | [No] | - | [rule] | [description] |

**Success Response** `200 OK`:

```json
{
  "data": [
    {
      "id": 1,
      "field_name": "value"
    }
  ],
  "pagination": {
    "page": 1,
    "per_page": 20,
    "total": 150,
    "total_pages": 8
  }
}
```

#### Get Single Resource

```
GET /api/v1/{resources}/{id}
```

**Path Parameters**:

| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| id | integer | Yes | Resource unique identifier |

**Success Response** `200 OK`:

```json
{
  "data": {
    "id": 1,
    "field_name": "value",
    "created_at": "2026-01-01T00:00:00Z",
    "updated_at": "2026-01-01T00:00:00Z"
  }
}
```

**Error Response** `404 Not Found`:

```json
{
  "error": {
    "code": "NOT_FOUND",
    "message": "Resource not found"
  }
}
```

#### Create Resource

```
POST /api/v1/{resources}
```

**Request Headers**:

| Header | Value | Required |
|--------|-------|----------|
| Content-Type | application/json | Yes |
| Authorization | Bearer {token} | Yes |

**Request Body**:

| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| field_name | string | Yes | max: 255 | [description] |
| email | string | Yes | valid email | [description] |

**Success Response** `201 Created`:

```json
{
  "data": {
    "id": 1,
    "field_name": "value",
    "created_at": "2026-01-01T00:00:00Z"
  }
}
```

**Error Response** `422 Unprocessable Entity`:

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Validation failed",
    "details": [
      {
        "field": "email",
        "message": "Must be a valid email address"
      }
    ]
  }
}
```

#### Update Resource (Full Replace)

```
PUT /api/v1/{resources}/{id}
```

**Request Body**: Same as Create

**Success Response** `200 OK`:

```json
{
  "data": {
    "id": 1,
    "field_name": "updated value",
    "updated_at": "2026-01-15T00:00:00Z"
  }
}
```

#### Update Resource (Partial)

```
PATCH /api/v1/{resources}/{id}
```

**Request Body**: Only fields to update

**Success Response** `200 OK`: Same as PUT

#### Delete Resource

```
DELETE /api/v1/{resources}/{id}
```

**Success Response** `204 No Content`: No response body

**Error Response** `404 Not Found`:

```json
{
  "error": {
    "code": "NOT_FOUND",
    "message": "Resource not found"
  }
}
```

### 3.2 Events/Messages

[If asynchronous messaging is involved, describe the message format and trigger conditions]

### 3.3 Error Responses Summary

| Status Code | Error Code | Description |
|-------------|------------|-------------|
| 400 | BAD_REQUEST | Malformed request syntax |
| 401 | UNAUTHORIZED | Missing or invalid authentication |
| 403 | FORBIDDEN | Insufficient permissions |
| 404 | NOT_FOUND | Resource does not exist |
| 409 | CONFLICT | Request conflicts with current state |
| 422 | VALIDATION_ERROR | Semantic validation failure |
| 429 | TOO_MANY_REQUESTS | Rate limit exceeded |
| 500 | INTERNAL_ERROR | Unexpected server error |

## 4. Data Model

[Database tables/models affected by this feature]

### 4.1 New/Modified Tables

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | bigint | PK, auto-increment | Primary key |
| created_at | timestamp | NOT NULL, default now() | Creation timestamp |
| updated_at | timestamp | NOT NULL, default now() | Last update timestamp |

### 4.2 Indexes

| Table | Columns | Type | Rationale |
|-------|---------|------|-----------|
| [table] | [columns] | UNIQUE / BTREE | [why this index] |

### 4.3 Data Migration

[Whether data migration is needed and the migration plan]

## 5. Non-Functional Requirements

### 5.1 Performance

- Expected QPS: [fill in]
- Response time: <= [fill in] ms (p99)
- Data volume: [fill in]

### 5.2 Security

- [ ] Authentication required
- [ ] Authorization (role/permission check)
- [ ] Data encryption (at rest / in transit)
- [ ] Input validation
- [ ] Sensitive data masking

### 5.3 Compatibility

- Backward compatible: Yes / No
- Versions to support: [fill in]
- Rollout strategy: [canary / blue-green / feature flag]

## 6. Acceptance Criteria

- [ ] [Criterion 1]
- [ ] [Criterion 2]
- [ ] [Criterion 3]

## 7. Test Scenarios

### Happy Path

- [Test scenario 1]
- [Test scenario 2]

### Edge Cases

- [Edge case 1]
- [Edge case 2]

### Error Scenarios

- [Error scenario 1]
- [Error scenario 2]

## 8. Technical Approach (Agent Section)

> This section is filled in by the AI agent based on the Spec content.

[Agent fills in the implementation approach]

## 9. Changelog

| Date | Version | Change | Author |
|------|---------|--------|--------|
| | v1.0 | Initial version | |
