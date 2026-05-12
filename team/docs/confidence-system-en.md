# Documentation Confidence System

> Defines a quality annotation system for technical documentation that lets teams distinguish between "AI-inferred" and "human-verified" content.
> All AI-generated technical documentation should follow this system for annotation.

---

## 1. Confidence Level Definitions

### Four-Level Annotation

| Marker | Name | Meaning | Applicable Scenarios |
|--------|------|---------|---------------------|
| 🟢 | High Confidence | AI confirmed from code | Code has clear naming, comments, or type definitions; AI can directly correspond |
| 🟡 | Medium Confidence | AI inferred, needs human confirmation | AI inferred from code logic but lacks direct evidence or naming is unclear |
| 🔴 | Low Confidence | AI cannot determine | May come from outdated docs, unclear code logic, or business meaning depends on context |
| 🔵 | Human Confirmed | Verified by a human | Confirmed by an experienced team member; information is accurate and reliable |

### Annotation Principles

1. **Default annotation**: All AI-generated content is initially annotated 🟢 or 🟡; 🔵 never appears from AI
2. **Better lower than higher**: If uncertain, annotate at the lower confidence level, not higher
3. **Must annotate**: Business rules, design decisions, and data meanings in docs must be annotated
4. **No annotation for pure facts**: Purely factual descriptions like code structure and API paths don't need confidence annotation

---

## 2. Markdown Annotation Format

### 2.1 Inline Annotation

Annotate inline text in parentheses:

```markdown
After order creation, status defaults to "Pending Payment" 🟢
Auto-cancellation after 30 minutes without payment 🟡 — Inferred from timeout check logic in ScheduledTask
Inventory auto-restored after refund 🔴 — No explicit inventory restoration logic found in code
Order amount includes shipping 🔵 — Confirmed by John on 2026-05-10
```

### 2.2 Table Annotation

Add a "Confidence" column to tables:

```markdown
## Order State Transitions

| Current State | Trigger | Target State | Confidence |
|---------------|---------|-------------|------------|
| Pending Payment | Payment success callback | Paid | 🟢 |
| Paid | Timeout, no shipment | Cancelled | 🟡 Inferred from timeout logic |
| Paid | Seller ships | Shipped | 🟢 |
| Shipped | Buyer confirms receipt | Completed | 🔵 2026-05-10 John |
| Completed | Buyer requests refund | Refunding | 🔴 Refund process needs confirmation |
```

### 2.3 Section-Level Annotation

Annotate entire sections:

```markdown
## 3. Discount Calculation Rules 🟡

> This section was inferred by AI from the PricingEngine class. Calculation rules need human confirmation.

- Regular users: No discount
- VIP users: 5% off
- SVIP users: 10% off
- During promotions: Additional 10% off (stackable)

Inference basis: discountRate calculation logic in PricingEngine.calculate() method
```

### 2.4 Uncertain Items Summary

Summarize all low-confidence items at the end of the document:

```markdown
---

## Uncertain Items List

| Item | Confidence | Location | Suggested Confirmation Method |
|------|-----------|----------|------------------------------|
| Refund process state transitions | 🔴 | Section 3.2 | Contact original payment module developer |
| Whether promotion discounts are stackable | 🟡 | Section 3, Discount Rules | Check product requirements doc |
| Inventory reservation timeout duration | 🟡 | Section 2, Inventory Management | Check Redis TTL configuration |
```

---

## 3. Confluence Annotation Format

### 3.1 High Confidence Content

Display directly without special annotation:

```html
<p>After order creation, data is written to the <code>orders</code> table.</p>
```

### 3.2 Medium Confidence Content

Use a yellow info panel:

```html
<ac:structured-macro ac:name="info">
  <ac:parameter ac:name="color">Yellow</ac:parameter>
  <ac:rich-text-body>
    <p><strong>AI Inferred (Needs Confirmation)</strong></p>
    <p>VIP users enjoy a 5% discount.</p>
    <p><em>Inference basis: discountRate calculation logic in PricingEngine.</em></p>
    <p><em>If confirmed correct, please remove this panel and mark confidence as 🔵.</em></p>
  </ac:rich-text-body>
</ac:structured-macro>
```

### 3.3 Low Confidence Content

Use a red warning panel:

```html
<ac:structured-macro ac:name="warning">
  <ac:rich-text-body>
    <p><strong>Uncertain Item</strong></p>
    <p>The state transition path for the refund process is unclear.</p>
    <p><em>Please contact the original module owner for confirmation, or check git history.</em></p>
  </ac:rich-text-body>
</ac:structured-macro>
```

### 3.4 Human Confirmed Content

Use a green success panel:

```html
<ac:structured-macro ac:name="info">
  <ac:parameter ac:name="color">Green</ac:parameter>
  <ac:rich-text-body>
    <p><strong>Human Confirmed</strong></p>
    <p>Order amount includes shipping.</p>
    <p><em>Confirmed by: John | Date: 2026-05-10</em></p>
  </ac:rich-text-body>
</ac:structured-macro>
```

---

## 4. Confidence Upgrade Process

### 4.1 Low-to-High Upgrade Path

```
    🔴 Low Confidence (AI cannot determine)
         ↓
    Find git history, check requirement docs, look at test cases
         ↓
    🟡 Medium Confidence (clues exist but uncertain)
         ↓
    Ask experienced team members
         ↓
    🟢 High Confidence (AI confirmed from code) or directly to 🔵
         ↓
    Verified by human as correct
         ↓
    🔵 Human Confirmed (final state)
```

### 4.2 Upgrade Operations

Each confidence upgrade requires:

| Operation | Description |
|-----------|-------------|
| Update marker | Change the marker in documentation from 🟡 to 🔵 |
| Record confirmer | Note the confirmer and confirmation date next to the marker |
| Remove hint panels | Change yellow/red Confluence panels to green or remove |
| Commit changes | Commit updated documentation to codebase or Confluence |

### 4.3 Confirmation Record Format

```markdown
## Confirmation Records

| Date | Document | Previous Confidence | New Confidence | Confirmed By | Notes |
|------|----------|-------------------|----------------|-------------|-------|
| 2026-05-10 | docs/api/orders.md | 🟡 | 🔵 | John | Refund process confirmed |
| 2026-05-12 | docs/architecture/data-model.md | 🔴 | 🟡 | - | Found partial git records |
```

---

## 5. Document Review Process

### 5.1 Review Priority

Review from lowest to highest confidence:

| Priority | Confidence | Review Method | Time Investment |
|----------|-----------|---------------|-----------------|
| P0 | 🔴 Low Confidence | Must review, confirm each item | ~5-10 min per item |
| P1 | 🟡 Medium Confidence | Key review, sample confirmation | ~2-5 min per item |
| P2 | 🟢 High Confidence | Quick scan, verify key points | ~5-10 min per document |
| P3 | 🔵 Human Confirmed | Periodic spot-check | Monthly |

### 5.2 Review Checklist

For each document, check:

- [ ] High-confidence items: Does code still correspond? (quick confirmation)
- [ ] Medium-confidence items: Is AI's inference reasonable?
- [ ] Low-confidence items: Can you find a confirmation source?
- [ ] New features: Have they been added to documentation?
- [ ] Removed features: Have they been noted in documentation?
- [ ] Confidence markers: Do any need upgrading or downgrading?

### 5.3 Review Report Template

```markdown
# Document Review Report

## Review Scope
- Document: [document name]
- Reviewer: [name]
- Review date: [date]

## Review Results

| Section | Previous Confidence | Review Conclusion | Action |
|---------|-------------------|-------------------|--------|
| 3.2 State Transitions | 🟡 | Confirmed correct | Upgrade to 🔵 |
| 4.1 Discount Rules | 🟡 | Difference found | Update to actual rules, keep 🟡 |
| 5.3 Refund Process | 🔴 | Found confirmer | Upgrade to 🔵 |

## Open Issues
- [ ] [Unresolved issue]
```

---

## 6. Team Usage Guidelines

### 6.1 When Generating Documentation

- AI-generated docs are automatically annotated with 🟢 and 🟡
- Uncertain parts are annotated with 🔴
- Do not annotate at a higher level just to make things "look good"

### 6.2 When Reviewing Documentation

- Confirmed content gets upgraded to 🔵 after review
- Incorrect content is marked 🔴 with correction notes
- Review records are saved at the end of the document

### 6.3 During Ongoing Maintenance

- Monthly audit: Check if all 🔴 items can be upgraded
- Code changes: Check if affected documentation confidence needs adjustment
- Team members can confirm any item at any time (upgrade to 🔵)

### 6.4 When New Members Use Docs

- New members should prioritize reading 🔵 and 🟢 annotated content
- 🟡 annotated content can be used as reference but note potential inaccuracies
- 🔴 annotated content should not be used as the basis for decisions

---

## 7. Metrics Tracking

### 7.1 Key Metrics

| Metric | Calculation | Target |
|--------|------------|--------|
| High confidence ratio | (🟢 + 🔵) / total items | ≥ 70% |
| Human confirmed ratio | 🔵 / total items | ≥ 40% |
| Low confidence ratio | 🔴 / total items | ≤ 10% |
| Average confirmation speed | Average days from 🔴 to 🔵 | ≤ 30 days |

### 7.2 Monthly Report

Track confidence distribution changes monthly:

```markdown
# Confidence Monthly Report - May 2026

## Distribution Overview
| Confidence | Items | Ratio | Last Month | Trend |
|-----------|-------|-------|------------|-------|
| 🟢 High Confidence | 45 | 35% | 40% | -5% |
| 🟡 Medium Confidence | 30 | 23% | 30% | -7% |
| 🔴 Low Confidence | 13 | 10% | 15% | -5% |
| 🔵 Human Confirmed | 41 | 32% | 15% | +17% |

## Improvements
- Human confirmed ratio increased from 15% to 32%
- Low confidence items decreased from 15% to 10%
- 26 new confirmation records added
```
