# Skill: AI-Assisted Code Review

> Purpose: Standardized code review workflow combining automated AI detection with human review.

## Trigger

- A Merge Request / Pull Request is submitted
- Code review stage

## Review Dimensions

### Dimension 1: Functional Correctness

- [ ] Code implements the functionality described in the Spec
- [ ] Edge cases are handled
- [ ] Error handling is complete
- [ ] No obvious logic errors

### Dimension 2: Security

- [ ] No SQL injection risks
- [ ] No XSS risks
- [ ] No sensitive information leakage (logs, responses)
- [ ] Authorization checks are complete
- [ ] No command injection risks

### Dimension 3: Performance

- [ ] No N+1 queries
- [ ] Database queries have appropriate indexes
- [ ] No unnecessary full-table scans
- [ ] No memory leak risks (large collections, unclosed resources)

### Dimension 4: Maintainability

- [ ] Naming is clear and consistent
- [ ] Functions/methods are not excessively long (> 50 lines needs attention)
- [ ] No duplicated code
- [ ] Comments are sufficient (especially for complex logic)

### Dimension 5: Testing

- [ ] Corresponding tests exist
- [ ] Tests cover core scenarios
- [ ] Tests are reliable (do not depend on external state)

### Dimension 6: Standards Compliance

- [ ] Follows coding standards in CLAUDE.md
- [ ] Follows project architecture layers
- [ ] Commit message format is correct

### Dimension 7: RESTful Compliance (for API changes)

- [ ] URL uses plural nouns (e.g., `/users`, not `/user`)
- [ ] Correct HTTP method used for the action
- [ ] Appropriate HTTP status codes returned
- [ ] JSON request/response follows project conventions
- [ ] Pagination implemented for collection endpoints

## Execution Steps

### Step 1: AI Automated Scan

The AI agent performs the following automated checks:

1. **Static analysis**: Run lint tools
2. **Security scan**: Check for common vulnerability patterns
3. **Complexity analysis**: Flag high-complexity functions
4. **Standards check**: Validate against CLAUDE.md conventions
5. **Test coverage**: Check test coverage for changed files

Generate an **AI Review Report**:
```
## AI Code Review Report

### Overview
- Files changed: [N]
- Lines changed: +[N] / -[N]
- Risk level: [Low / Medium / High]

### Issues Found
| # | Type | Severity | File:Line | Description |
|---|------|----------|-----------|-------------|
| 1 | Security | High | foo.js:42 | Potential SQL injection |

### Recommendations
- [Specific recommendations]
```

### Step 2: Human In-Depth Review

Developers focus their review based on the AI report:

1. **High-risk items**: Issues flagged as high severity by AI
2. **Business logic**: Correctness that AI cannot judge
3. **Architectural impact**: How the changes affect the overall architecture
4. **AI-generated code**: If tagged `[AI-Generated]`, pay extra attention to architectural soundness

### Step 3: Feedback & Revision

- Review comments are categorized as: `Must Fix` / `Suggestion` / `Question`
- `Must Fix` items must be resolved before merging
- Submitting revisions triggers re-review

## Notes

- AI review is supplementary; it does not replace human review
- Security and business logic changes must always be reviewed by humans
- Keep review turnaround fast to avoid becoming a bottleneck
- Document AI false positives to improve future rules
