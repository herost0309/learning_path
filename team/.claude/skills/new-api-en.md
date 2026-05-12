# Skill: Create New API Endpoint

> Purpose: Standardized workflow for creating new API endpoints, ensuring consistency and RESTful compliance.

## Trigger

Use this skill when you need to create a new API endpoint.

## Input Requirements

Before using this skill, provide the following information:

- **Resource name**: e.g., `users`, `orders`, `products`
- **HTTP method**: GET / POST / PUT / PATCH / DELETE
- **Business description**: The business purpose of the endpoint
- **Request parameters**: Parameter name, type, required/optional, validation rules
- **Response format**: Success and error response structures
- **Authorization requirements**: Authentication and role/permission needed

## Execution Steps

### Step 1: Write the Spec

Create an endpoint specification file in the `docs/specs/` directory:

```markdown
## [Endpoint Name] Spec

### Basic Information
- Resource: [resource name, plural noun]
- Method: [HTTP method]
- Path: [URL path]
- Description: [business purpose]

### Request

#### Headers
| Header | Value | Required |
|--------|-------|----------|
| Authorization | Bearer {token} | Yes |
| Content-Type | application/json | Yes (POST/PUT/PATCH) |

#### Path Parameters
| Parameter | Type | Description |
|-----------|------|-------------|
| id | integer | Resource unique identifier |

#### Query Parameters (for GET / collection endpoints)
| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| page | integer | No | 1 | Page number |
| per_page | integer | No | 20 | Items per page (max 100) |
| sort | string | No | created_at | Sort field |
| order | string | No | desc | Sort direction (asc/desc) |

#### Request Body (for POST / PUT / PATCH)
| Field | Type | Required | Validation | Description |
|-------|------|----------|------------|-------------|
| | | | | |

### Response

#### Success Response
- Status: [200 / 201 / 204]
```json
{
  "data": {
    "id": 1,
    "...": "..."
  }
}
```

#### Error Responses
- 400 Bad Request
- 401 Unauthorized
- 403 Forbidden
- 404 Not Found
- 422 Unprocessable Entity

### Authorization
- [Required role/permission]
```

### Step 2: Generate Code

Based on the Spec, generate the following files:

1. **Route definition** - Register the new endpoint in the router
2. **Controller/Handler** - Request handling logic
3. **Request validation** - Input validation (using the project's validation framework)
4. **Business logic** - Service layer implementation
5. **Data access** - Repository/DAO layer (if database operations needed)
6. **Unit tests** - Cover happy path, edge cases, and error scenarios

### Step 3: Checklist

- [ ] Parameter validation is complete (type, range, required)
- [ ] Error handling uses unified error classes with proper HTTP status codes
- [ ] Authorization checks are in place (if required)
- [ ] Database queries have index coverage
- [ ] API documentation is updated
- [ ] Unit tests pass with coverage >= 80%
- [ ] No security vulnerabilities (XSS, SQL injection, etc.)
- [ ] Follows RESTful naming conventions (nouns, not verbs; plural collections)
- [ ] Correct HTTP status codes for each response type

### Step 4: Commit

Commit message format: `feat: add [resource name] [action] endpoint`

Examples:
- `feat: add users CRUD endpoints`
- `feat: add GET /users/:id/orders endpoint`

## Notes

- Follow RESTful conventions strictly (see CLAUDE.md Section 8)
- Use the correct HTTP method for the action (GET=read, POST=create, PUT=replace, PATCH=update, DELETE=remove)
- Use proper status codes (201 for creation, 204 for deletion, etc.)
- Follow the project's existing layered architecture
- For complex queries, use Specification / Query Builder; never concatenate raw SQL
