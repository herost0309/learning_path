# Skill: Legacy Module Refactoring

> Purpose: Execute a "Map First, Modify Later" refactoring workflow for legacy code modules.
> This is the core skill for addressing technical debt.

## Trigger

- Refactoring a legacy module
- Technical debt cleanup
- Module performance optimization

## Input Requirements

- **Target module**: Path to the module to refactor
- **Refactoring goal**: Why refactor (performance, maintainability, preparation for new features)
- **Constraints**: External interfaces/behaviors that must not be broken

## Execution Steps

### Phase 1: Mapping (Understand First, Don't Touch)

#### Step 1.1: Module Map Generation

Analyze the module and generate the following map:

```markdown
## Module Map: [module name]

### Basic Information
- File count: [N]
- Total lines: [N]
- Primary language: [language]
- External dependencies: [list]

### Data Flow
[Describe the data flow path from entry point to exit]

### Dependencies
[Call relationships between components within the module]

### Public Interfaces
[List of interfaces exposed for external use]

### Risk Areas
| Area | Risk Type | Severity | Description |
|------|-----------|----------|-------------|
| [function/file] | [type] | [High/Medium/Low] | [description] |

### Technical Debt Inventory
| # | Debt | Impact | Recommendation |
|---|------|--------|----------------|
| 1 | [description] | [impact] | [recommendation] |
```

#### Step 1.2: Test Safety Net

**Before modifying any code, a test safety net must be established**:

1. Run the `generate-tests` skill to produce unit tests for the module
2. If tests already exist, run them first to confirm they all pass and record the current state
3. For modules that are difficult to unit test, write integration tests first as a safety net

### Phase 2: Planning (Design the Approach)

#### Step 2.1: Refactoring Plan

Write a refactoring plan document (`docs/specs/refactor-[module-name].md`):

```markdown
## Refactoring Plan: [module name]

### Goal
[Specific, measurable objectives]

### Step-by-Step Plan
| Step | Change Description | Risk | Estimated Impact |
|------|--------------------|------|------------------|
| 1 | [first change] | [risk rating] | [impact scope] |

### Invariants
[Behaviors/interfaces that must remain unchanged during refactoring]

### Rollback Strategy
[How to roll back each step]
```

### Phase 3: Execution (Small Steps Forward)

#### Step 3.1: Incremental Refactoring

Follow the **small commit** principle:

1. **One thing at a time** - Do not mix multiple refactoring types in a single commit
2. **Run tests after every step** - Ensure changes do not break existing functionality
3. **Commit granularity** - One commit per logically complete refactoring step

Common refactoring techniques (by priority):

1. **Rename** - Improve naming for readability
2. **Extract function** - Split overly long functions
3. **Eliminate duplication** - Extract shared logic
4. **Simplify conditionals** - Simplify complex if/else logic
5. **Introduce parameter object** - Reduce parameter count
6. **Replace temp variable** - Replace with query
7. **Encapsulate collection** - Do not expose internal collections

#### Step 3.2: Continuous Verification

After each refactoring step:
- [ ] Run all tests and ensure they pass
- [ ] Check for no significant performance regression
- [ ] Verify public interface behavior is unchanged
- [ ] Code review

### Phase 4: Wrap-Up

#### Step 4.1: Refactoring Report

```markdown
## Refactoring Report: [module name]

### Change Statistics
- Files: [N]
- Lines deleted: [N]
- Lines added: [N]
- Function count: [N] -> [N]

### Quality Improvements
- Test coverage: [old]% -> [new]%
- Cyclomatic complexity: [old] -> [new]
- Duplication rate: [old]% -> [new]%

### Remaining Issues
- [Unresolved technical debt]
```

## Notes

- **Refactoring does not change behavior** - If you need to both change functionality and refactor, do them in separate steps
- **Don't aim for perfection** - Improve incrementally and sustain the effort
- **Prioritize the "most painful" modules** - Start with those that have the heaviest maintenance burden
- **1-2 modules per month** - Do not spread effort too thin at once
