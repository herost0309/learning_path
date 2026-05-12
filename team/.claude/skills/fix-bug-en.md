# Skill: Fix Bug

> Purpose: Standardized bug fix workflow ensuring root cause analysis and regression prevention.

## Trigger

Use this skill when a bug report is received or a production issue is discovered.

## Input Requirements

- **Bug description**: What is the observed behavior
- **Reproduction steps**: How to reproduce the issue
- **Expected behavior**: What should happen instead
- **Error information**: Logs/error messages (if available)

## Execution Steps

### Step 1: Reproduce and Analyze

1. Attempt to reproduce the bug following the provided steps
2. Analyze the related code to locate the root cause
3. Check for associated known issues

**Output**: Root cause analysis (documented in code comments or MR description)

### Step 2: Write a Failing Test

Before fixing, **write a test case that reproduces the bug**:

- Test name format: `test_[function]_should_[expected_behavior]_when_[condition]`
- The test must reliably reproduce the bug
- This test serves as a regression safety net

### Step 3: Implement the Fix

1. **Minimal change principle**: Only modify the code necessary to fix the bug; do not include unrelated refactoring
2. After fixing, confirm the failing test now passes
3. Check whether the fix affects other functionality (run the related test suite)

### Step 4: Impact Assessment

- [ ] Does the fix affect other modules?
- [ ] Is data migration/repair needed?
- [ ] Does documentation need updating?
- [ ] Do other teams need to be notified?

### Step 5: Commit

Commit message format: `fix: [brief problem description] (#issue-number)`

MR description must include:
- **Root cause**: Why the bug occurred
- **Fix approach**: How it was fixed
- **Tests**: What tests were added to prevent regression
- **Impact scope**: Modules that may be affected

## Notes

- Do not mix refactoring or other changes into a bug fix PR
- If the bug reveals a deeper architectural issue, record it in `docs/specs/` as a follow-up refactoring task
- For critical production hotfixes, some steps may be skipped but tests must be added retroactively
