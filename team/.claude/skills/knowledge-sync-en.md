# Skill: Knowledge Base Long-Term Sync

> Purpose: Ensure technical documentation stays in sync with code, establishing a "code change → doc update" long-term maintenance mechanism.
> Core problem solved: Documentation becomes outdated after writing, nobody maintains it, and it eventually becomes "historical artifacts."

## Trigger Conditions

- Code changes require checking if documentation needs updating
- Periodic auditing of documentation vs. code consistency
- Pre-publication validation of new documentation accuracy
- Checking for missed documentation updates during code review

---

## 1. Code → Documentation Sync

### 1.1 Change Detection

When code changes, AI automatically detects affected documentation:

```
Prompt:
Analyze the following code changes and determine which documentation needs updating.

Changes:
[Paste git diff or list of changed files]

Checklist:
1. Added/modified API endpoints → docs in docs/api/
2. Modified data models (added/removed/changed fields) → docs/architecture/data-model.md
3. Modified business logic (if/else conditions, calculation formulas) → corresponding business rules docs
4. Added/modified configuration items → ops manuals in docs/ops/
5. Modified inter-module dependencies → architecture docs in docs/architecture/
6. Modified external service calls → related integration docs

Output:
- List of affected documents
- Specific sections needing updates
- Suggested update content
```

### 1.2 Incremental Updates

```
Prompt:
The following code changes have occurred. Update the corresponding documentation.

Changes:
[Describe changes]

Current documentation:
[Paste current doc content]

Requirements:
1. Only update affected sections; preserve unchanged content
2. Maintain consistent documentation style
3. Mark updates with change date
4. If changes introduce new concepts or interfaces, add complete descriptions

Output: Complete updated document
```

### 1.3 CI/CD Integration Template

Add a documentation sync check step in your CI pipeline:

```yaml
# .gitlab-ci.yml example
doc-sync-check:
  stage: verify
  script:
    - |
      # Check if changed files involve documentation
      CHANGED_FILES=$(git diff --name-only origin/main...HEAD)
      CODE_FILES=$(echo "$CHANGED_FILES" | grep -E '\.(java|py|js|ts|go)$' || true)
      DOC_FILES=$(echo "$CHANGED_FILES" | grep -E '^docs/' || true)

      if [ -n "$CODE_FILES" ] && [ -z "$DOC_FILES" ]; then
        echo "Warning: Code changed but no docs updated"
        echo "Changed code files:"
        echo "$CODE_FILES"
        echo ""
        echo "Consider updating related documentation."
      fi
  allow_failure: true
```

---

## 2. Documentation → Code Reverse Validation

### 2.1 Documentation Accuracy Validation

```
Prompt:
Below is a technical document. Validate its accuracy against the current codebase.

Document content:
[Paste document]

Validation dimensions:
1. Do the interfaces described in the doc actually exist in code?
2. Is the parameter list consistent with the code?
3. Is the business logic described consistent with the implementation?
4. Does the data model match the current code?
5. Are the configuration items mentioned still valid?

Annotate each validation point:
- [Pass] Documentation matches code
- [Difference] Documentation differs from code; list specific differences
- [Missing] Content described in docs cannot be found in code
- [New] Code has content not mentioned in docs

Output: Validation report
```

### 2.2 Outdated Documentation Detection

```
Prompt:
Scan all documents under docs/ directory and detect outdated content.

Detection methods:
1. Read file paths referenced in docs; check if files exist
2. Read API endpoints referenced in docs; check if they exist in route definitions
3. Read configuration items referenced in docs; check if they exist in config files
4. Read code examples in docs; check if they match current code
5. Check document last-update time; flag anything over 3 months as "needs review"

Output: Outdated content list, sorted by severity
```

---

## 3. Confidence Annotation System

Annotate each piece of documentation content with a confidence level so readers know how reliable the information is.

### 3.1 Confidence Level Definitions

| Marker | Meaning | Description |
|--------|---------|-------------|
| 🟢 High Confidence | AI confirmed from code | Clear naming, comments, or direct correspondence in code |
| 🟡 Medium Confidence | AI inferred, needs human confirmation | AI inferred from code logic but lacks direct evidence |
| 🔴 Low Confidence | AI cannot determine | May come from outdated docs, or code logic is unclear |
| 🔵 Human Confirmed | Verified by a human | Confirmed by an experienced team member |

### 3.2 Markdown Annotation Format

In Markdown documents, use the following format to annotate confidence:

```markdown
## Order State Transitions

| Current State | Trigger | Target State | Confidence |
|---------------|---------|-------------|------------|
| Pending Payment | Payment callback | Paid | 🟢 High Confidence |
| Paid | Timeout, no shipment | Cancelled | 🟡 Medium Confidence — Inferred from timeout logic, confirm business rule |
| Shipped | Buyer confirms receipt | Completed | 🔵 Human Confirmed — Verified by John on 2026-05-10 |

> ⚠️ **Low-confidence items need confirmation**:
> - 🔴 Does order refunding trigger state rollback? RefundService exists in code but state transition is unclear
```

### 3.3 Confluence Annotation Format

In Confluence, use info panels for annotation:

```html
<!-- High confidence content, displayed directly -->
<p>After order creation, data is written to the <code>orders</code> table.</p>

<!-- Medium confidence content, with hint box -->
<div style="background: #fff3cd; padding: 8px; border-left: 4px solid #ffc107; margin: 8px 0;">
  <strong>AI Inferred (Needs Confirmation)</strong><br>
  VIP users enjoy a 5% discount. Inference basis: discountRate calculation logic in PricingEngine.
  <br><em>If confirmed correct, please remove this hint box.</em>
</div>

<!-- Low confidence content, with warning box -->
<div style="background: #f8d7da; padding: 8px; border-left: 4px solid #dc3545; margin: 8px 0;">
  <strong>Uncertain Item</strong><br>
  The state transition path for the refund process is unclear. RefundService exists in code but its interaction with the order state machine needs human confirmation.
  <br><em>Please contact the original module owner for confirmation.</em>
</div>
```

---

## 4. Periodic Auditing

### 4.1 Monthly Audit Process

```
Prompt:
Execute a monthly documentation audit.

Steps:
1. List all documents under docs/
2. For each document:
   a. Get last update time (from git log)
   b. Read document content
   c. Check key information accuracy against code
   d. Flag sections needing updates
3. Generate audit report

Output format:
# Documentation Audit Report - [Year-Month]

## Summary
| Metric | Value |
|--------|-------|
| Total documents | ... |
| Outdated documents | ... |
| Documents needing updates | ... |
| High-confidence entries ratio | ... |

## Detailed Results
[Audit results listed by document]

## Recommended Actions
[Update actions listed by priority]
```

### 4.2 Audit Report Template

```markdown
# Documentation Audit Report - May 2026

## Summary

| Metric | Value | Last Month | Trend |
|--------|-------|------------|-------|
| Total documents | 23 | 21 | +2 |
| Outdated documents | 3 | 5 | -2 |
| High-confidence ratio | 78% | 72% | +6% |
| Human-confirmed ratio | 35% | 28% | +7% |

## Documents Needing Updates

| Document | Issue | Severity | Recommendation |
|----------|-------|----------|----------------|
| docs/api/orders.md | Missing new refund endpoint | High | Add endpoint documentation |
| docs/architecture/data-model.md | 3 new fields in orders table | Medium | Update field descriptions |
| docs/ops/deploy.md | Deploy script path changed | Low | Update path |

## Low-Confidence Items

| Document | Low-Confidence Item | Suggested Confirmer |
|----------|-------------------|-------------------|
| docs/architecture/payment.md | Refund reconciliation flow | Financial system owner |
```

---

## 5. Git Hook Integration

### 5.1 Pre-commit Check

```bash
#!/bin/bash
# .git/hooks/pre-commit
# Check if code changes require documentation updates

STAGED_FILES=$(git diff --cached --name-only --diff-filter=ACM)

# Check if code files changed
CODE_CHANGED=$(echo "$STAGED_FILES" | grep -cE '\.(java|py|js|ts|go|rb|php)$' || true)

# Check if documentation changed
DOC_CHANGED=$(echo "$STAGED_FILES" | grep -cE '^docs/' || true)

# Check if API routes changed
ROUTE_CHANGED=$(echo "$STAGED_FILES" | grep -cE '(routes|controller|api)' || true)

if [ "$CODE_CHANGED" -gt 0 ] && [ "$DOC_CHANGED" -eq 0 ]; then
    echo ""
    echo "⚠️  Code changed but documentation not updated"
    echo "   Changed code files:"
    echo "$STAGED_FILES" | grep -E '\.(java|py|js|ts|go|rb|php)$'
    echo ""
    echo "   Suggestion: If changes affect interfaces, data models, or business logic, update docs too."
    echo ""
fi

if [ "$ROUTE_CHANGED" -gt 0 ] && [ "$DOC_CHANGED" -eq 0 ]; then
    echo ""
    echo "❗ API routes changed but API documentation not updated"
    echo "   Please update the corresponding docs in docs/api/."
    echo ""
fi
```

### 5.2 MR/PR Checklist

Add documentation check items to the Merge Request template:

```markdown
## Documentation Sync Check

- [ ] Does this change affect API endpoints?
  - [ ] Updated corresponding docs in `docs/api/`
- [ ] Does this change modify data models?
  - [ ] Updated `docs/architecture/data-model.md`
- [ ] Does this change modify business logic?
  - [ ] Updated corresponding business rules docs
- [ ] Does this change modify configuration items?
  - [ ] Updated ops docs in `docs/ops/`
- [ ] New documentation content has confidence annotations
```

---

## Usage Tips

### Daily Usage

| Scenario | Usage |
|----------|-------|
| While coding | Run pre-commit check before pushing |
| During code review | Check if documentation was updated in sync |
| Beginning of each month | Run monthly audit, generate report |
| Before release | Check accuracy of all API documentation |

### Team Collaboration

- Share audit reports with the team so everyone knows which docs need attention
- Assign low-confidence items to the corresponding module owners for confirmation
- Spend 10 minutes in monthly team meetings going through the audit report

### Integration with Other Skills

- **code-archaeology**: After code archaeology produces docs, use knowledge-sync to maintain them
- **generate-docs**: Automatically annotate confidence when generating docs
- **code-review**: Check documentation sync status during reviews
