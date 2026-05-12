# AI Best Practices Library

> This directory accumulates team best practices for AI-assisted development, including prompt templates and use cases.
> Each team member should contribute at least 1 entry per month.

---

## Directory Structure

```
docs/ai-practices/
├── README.md              # This file
├── prompts/               # Prompt templates
│   ├── code-generation.md # Code generation prompts
│   ├── debugging.md       # Debugging prompts
│   └── refactoring.md     # Refactoring prompts
├── case-studies/          # Use cases (specific project success stories)
├── anti-patterns/         # Pitfall records (problems and solutions when using AI)
└── templates/             # Reusable templates
```

## Contribution Guide

### Case Document Naming

`case-studies/[YYYY-MM-DD]-[brief-description].md`

Example: `case-studies/2026-05-15-legacy-order-module-test-generation.md`

### Case Document Format

```markdown
# [Case Title]

## Background
[Scenario description]

## Method
[Specific approach: Prompt, Skill, or Agent configuration used]

## Results
- Quantitative: [specific numbers]
- Qualitative: [experience]

## Lessons Learned
[Summary and improvement ideas]

## Applicable Scenarios
[When to use this approach]
```

### Contributors

| Name | Contributions | Latest Contribution Date |
|------|--------------|------------------------|
| | | |

---

## Selected Prompt Techniques

### Code Generation

```
Role: You are a senior [language/framework] developer.
Task: Implement [feature name] based on the following Spec.
Constraints:
- Follow project coding standards (see CLAUDE.md)
- Use the project's existing [framework/library]
- Include error handling and parameter validation
- Include unit tests

Spec:
[Paste Spec content]
```

### Debugging Assistance

```
Role: You are a debugging expert.
Problem: [symptom description]
Error log:
[Paste log]
Related code:
[Paste code]

Analyze possible root causes and provide troubleshooting steps.
```

### Refactoring Assistance

```
Role: You are a code refactoring expert.
Goal: Refactor the following code to improve readability and maintainability.
Constraints:
- Do not change external behavior
- Follow [SOLID/DRY/KISS] principles
- Each change should be small and verifiable

Code:
[Paste code]
```

### RESTful API Generation

```
Role: You are a senior backend developer specializing in RESTful API design.
Task: Implement the following API endpoint based on the Spec.
RESTful conventions to follow:
- Use plural nouns for resource URLs (e.g., /users, not /user)
- Use correct HTTP methods (GET=read, POST=create, PUT=replace, PATCH=update, DELETE=remove)
- Return appropriate HTTP status codes (201 for creation, 204 for deletion, etc.)
- Use consistent JSON response format with { "data": {...} } wrapper
- Include pagination for collection endpoints
- Use proper error response format with { "error": { "code": "...", "message": "..." } }

Spec:
[Paste Spec content]
```
