# Skill: Generate Unit Tests

> Purpose: Use AI agents to automatically generate unit tests for existing code, rapidly improving test coverage.
> This is the core skill for Phase 1 "Quick Wins".

## Trigger

- Supplementing tests for legacy code
- Generating tests for new features
- Improving module test coverage

## Input Requirements

- **Target file/module**: Path to the code that needs tests
- **Coverage target**: Desired coverage percentage (default: 80%)
- **Test priority**: Core logic > Edge cases > Error paths

## Execution Steps

### Step 1: Analyze Target Code

1. Read the target file and understand:
   - Function/method inputs and outputs
   - Dependencies (database, external APIs, other modules)
   - Business logic branches
   - Boundary conditions
2. Generate a module analysis summary

### Step 2: Identify Test Scenarios

List test scenarios for each function/method:

| Function | Scenario | Type | Priority |
|----------|----------|------|----------|
| [name] | Normal input | Happy path | P0 |
| [name] | Null/empty value | Boundary | P1 |
| [name] | Invalid input | Error | P1 |
| [name] | Concurrency | Boundary | P2 |

### Step 3: Generate Test Code

Test code standards:

- **Test framework**: Use the project's existing test framework
- **Naming convention**: `test_[function]_should_[expectation]_when_[condition]`
- **Mock strategy**: Mock external dependencies; use real calls for internal logic
- **Data factory**: Use the Factory pattern to create test data; avoid hardcoding
- **Independence**: Each test case must run independently without depending on execution order

Test files should be placed in the corresponding `tests/` directory, mirroring the source file structure.

### Step 4: Run and Verify

1. Run the generated tests and ensure all pass
2. Check the coverage report
3. Mark uncovered scenarios and the reasons

### Step 5: Output Report

```
## Test Generation Report

### Module: [module name]
- Test file: [path]
- Test cases: [count]
- Coverage: [percentage]
- Covered scenarios:
  - [x] Happy path
  - [x] Boundary conditions
  - [ ] Error scenarios (reason: [explanation])
- Follow-up suggestions: [tests requiring manual supplementation]
```

## Legacy Code Test Strategy

When generating tests for legacy code, pay special attention to:

1. **Do not modify source code** - Write tests to understand behavior first, then consider refactoring
2. **Document unexpected behavior** - If tests reveal code behavior that differs from expectations, document it
3. **Use snapshot tests** - For complex outputs, use snapshot tests to lock in current behavior first
4. **Incremental improvement** - Cover the main path (P0) first, then expand to boundary and error cases

## Notes

- AI-generated tests require human review for business logic correctness
- Do not overuse mocks; excessive mocking diminishes test value
- Integration tests and E2E tests are not covered by this skill; they require separate planning
