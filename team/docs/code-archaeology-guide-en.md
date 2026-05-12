# Code Archaeology Practical Guide

> An operations guide for technical leads.
> Scenario: You've taken over a codebase with incomplete or no documentation. You need to use an AI Agent to quickly fill in documentation within a limited timeframe.

---

## 1. Assess Your Situation First

Before starting, spend 30 minutes evaluating the current state:

| Assessment Item | Your Situation | Notes |
|----------------|---------------|-------|
| Codebase size | Small (<50 files) / Medium (50-200) / Large (>200) | Determines effort and strategy |
| Existing docs | None / Partial / Outdated | Determines start-from-scratch vs. cross-reference |
| Team size | 1 person / 2-3 / More | Determines division of labor |
| Available time | 1 day / 1 week / 2 weeks | Determines depth and scope |
| Subject matter experts available | Yes / No | Determines validation strategy |

### Choose Strategy Based on Assessment

| Situation | Recommended Strategy |
|-----------|---------------------|
| Small codebase + No docs + 1 day | Phase 1 global scan + core module deep dive; produce panorama and API docs |
| Medium codebase + Partial docs + 1 week | Complete three-phase process, broken down by day |
| Large codebase + No docs + 1 week | Phase 1 global scan + priority-based deep dive into top 3 core modules |
| Any + 2 weeks | Complete three phases + cross-validation + doc sync mechanism |

---

## 2. One-Week Practical Plan (Medium Codebase)

Below is a 5-day practical plan for a medium codebase (50-200 files, 5-10 modules).

### Day 1: Global Scan — Produce Project Panorama

**Goal**: Build holistic understanding of the entire codebase; produce a panorama.

#### Morning: Environment Setup + Initial Scan

```
Steps:
1. Ensure the project builds and runs correctly
2. Use code-archaeology Skill Phase 1 to scan step by step
3. Run the "Project Panorama" prompt first
4. Then run the "Entry Point Map" prompt
```

**Prompt to use**:

```
Analyze the current project's complete codebase and generate a project panorama.
Requirements:
1. Identify the tech stack (framework, language version, build tools, key dependencies)
2. List directory structure with responsibility descriptions
3. List all business modules
4. Identify project type (monolith / microservices / serverless / library)
5. Estimate code scale
Output format: Markdown
```

#### Afternoon: Dependency Analysis + Doc Inventory

```
Steps:
1. Run the "Dependency Graph" prompt
2. Run the "Existing Documentation Inventory" prompt
3. Compile all outputs into a single document
```

**Day 1 Outputs**:
- Project panorama (tech stack, directory structure, module inventory)
- Entry point map (API endpoints, scheduled tasks, message consumers)
- Dependency graph (inter-module, external services)
- Documentation inventory (existing vs. missing)

**Checkpoint**:
- [ ] Can describe what the project does in one sentence
- [ ] Know all module names and approximate responsibilities
- [ ] Know which external services the project depends on
- [ ] Know which documents exist and which are missing

---

### Day 2: Core Business Module Deep Dive (First 2 Modules)

**Goal**: Deep-dive into the most critical business modules.

#### Selecting Modules

Choose 2 modules for today's deep dive based on:

1. Modules directly involved in core business processes
2. Modules with the most code
3. Modules most depended upon by other modules

#### For Each Module, Execute:

**Step 1: Code vs. Documentation Cross-Reference** (if docs exist)

```
Prompt:
Analyze the code under [module path] and cross-reference with the following existing documentation:
[Paste existing docs or note "No existing documentation"]

Requirements:
1. Summarize the module's actual functionality
2. Differences from existing documentation
3. Features in code not mentioned in docs
4. Severity of each difference
```

**Step 2: Extract Business Rules**

```
Prompt:
Deep-dive into the code under [module path] and extract business rules.
Focus areas:
1. Business meaning of if/else conditional branches
2. Business meaning of enum values and constants
3. State machine logic
4. Validation rules
5. Calculation logic
Annotate each rule with confidence level (High/Medium/Low).
```

**Step 3: Trace Data Flows**

```
Prompt:
Trace the main data flows in [module path].
For each API endpoint, map: Request entry → Processing steps → Data reads/writes → Response
Annotate code location (file:line) at each step.
```

**Day 2 Outputs**:
- Module 1 business rules table (with confidence annotations)
- Module 1 data flow diagrams
- Module 2 business rules table (with confidence annotations)
- Module 2 data flow diagrams

**Checkpoint**:
- [ ] Understand core business logic of both modules
- [ ] Every business rule has a confidence annotation
- [ ] Data flows for main APIs are clearly traced
- [ ] Uncertain items are listed with suggested confirmation methods

---

### Day 3: Remaining Module Deep Dives + Confirm Uncertain Items

**Goal**: Complete deep-dive analysis of all modules; confirm items flagged as uncertain on Day 2.

#### Morning: Continue Module Deep Dives

Apply the same Day 2 process to remaining modules by priority.

#### Afternoon: Confirm Uncertain Items

Take the "Uncertain Items List" from Day 2 and find someone who knows the system:

```
Confirmation approach:
1. Send the uncertain items list to experienced colleagues
2. For each uncertain item, note:
   - What AI inferred
   - Why AI is uncertain
   - What needs confirmation
3. After confirmation, update confidence: 🟡/🔴 → 🔵
```

**If no one is available to confirm**:

- Use git blame / git log to find the author of related code
- Check commit messages when code was submitted
- Look at related requirement documents or JIRA tickets
- Keep 🟡 annotations in docs, confirm when opportunity arises

**Day 3 Outputs**:
- Business rules tables for all modules
- Data flow diagrams for all modules
- Confirmation results for uncertain items

---

### Day 4: Existing Doc Cross-Reference + Difference Annotation

**Goal**: Comprehensively compare AI analysis results with existing docs; produce a difference report.

#### Full Comparison

```
Prompt:
Compare the following AI analysis results with existing documentation in full.

AI analysis results:
[Paste all module analysis outputs]

Existing documentation:
[List paths to all existing docs]

Requirements:
1. Compare document by document
2. Annotate: Consistent / Different / AI new finding / Described in docs but absent in code
3. Assess severity of each difference
4. Suggest update actions

Output: Difference report
```

#### Produce Difference Report

```markdown
# Documentation Difference Report

## Summary
| Metric | Count |
|--------|-------|
| Existing documents | ... |
| Documents consistent with code | ... |
| Documents needing updates | ... |
| Documents to delete (describing non-existent features) | ... |
| Documents to create | ... |

## Difference Details
### docs/api/orders.md
| Difference Type | Description | Severity |
|----------------|-------------|----------|
| Missing | Refund endpoint not in docs | High |
| Outdated | Order status enum has 2 new values | Medium |
| Incorrect | Parameter name changed | High |
```

**Day 4 Outputs**:
- Complete documentation difference report
- List of documents to update / create / delete

---

### Day 5: Produce Complete Document Set + Publish

**Goal**: Based on three days of analysis, produce final documents and publish.

#### Morning: Generate Documentation

Use code-archaeology Skill Phase 3 prompts to generate each:

| Document Type | Prompt Used | Output Location |
|--------------|-------------|-----------------|
| Module architecture docs | Phase 3.1 | `docs/architecture/[module-name].md` |
| Data model documentation | Phase 3.2 | `docs/architecture/data-model.md` |
| API documentation | Phase 3.3 | `docs/api/[module-name].md` |
| Risk map | Phase 3.4 | `docs/architecture/risk-map.md` |

Each document must include:
- Confidence annotations (per confidence-system specification)
- Generation timestamp and base commit hash
- "Uncertain Items" section

#### Afternoon: Review + Publish

**Review Checklist**:

- [ ] First sentence of each doc explains what the module does
- [ ] API docs cover all entry points
- [ ] Data model docs cover all tables/entities
- [ ] Low-confidence items have clear confirmation suggestions
- [ ] No sensitive information (keys, internal IPs)
- [ ] Mermaid diagrams render correctly

**Publishing Options**:

1. **In codebase**: Commit to `docs/` directory, tag MR with `[AI-Generated]`
2. **Confluence**: Use the generate-docs Skill publishing flow
3. **Team sharing**: Spend 15 minutes in a team meeting going through the panorama and key findings

#### Post-Publishing Document Maintenance

Set up a long-term sync mechanism in the project:

1. Add `.git/hooks/pre-commit` (see knowledge-sync Skill)
2. Set up monthly audit calendar reminder
3. Add documentation check items to MR template

**Day 5 Outputs**:
- Complete document set (architecture, API, data model, risk map)
- Docs published to target location (codebase/Confluence)
- Long-term sync mechanism established

---

## 3. Handling "AI Is Uncertain" Situations

### Common Uncertainty Scenarios

| Scenario | AI Behavior | How to Handle |
|----------|------------|---------------|
| Abbreviated variable names | AI cannot infer business meaning | Use git blame to find original author |
| Complex nested if/else | AI can analyze code but not business intent | Mark as 🟡, await human confirmation |
| Async logic | AI may miss callbacks/event handlers | Trace full call chain before annotating |
| Commented-out code | Uncertain if deprecated or temporarily disabled | Check git log to confirm |
| Hardcoded magic numbers | AI can identify but not determine meaning | Mark as 🟡, suggest extracting to constant |
| Third-party library implicit behavior | AI may not understand library internals | Check library documentation |

### Confidence Upgrade Path

```
🔴 Low Confidence (AI cannot determine)
  ↓ Find related git commits, check commit messages
🟡 Medium Confidence (clues exist but uncertain)
  ↓ Ask experienced team members or check requirement docs
🟢 High Confidence (AI confirmed from code)
  ↓ Verified by human as correct
🔵 Human Confirmed (final state)
```

### Who to Ask for Confirmation

| Person to Confirm | Applicable Scenario | Efficiency |
|------------------|-------------------|------------|
| Original module developer | Business logic, design decisions | Highest (if still on team) |
| Tech lead | Architecture decisions, tech choices | High |
| Product manager | Business rules, requirement context | Medium (understands business not implementation) |
| QA engineer | Edge cases, exception scenarios | Medium (reverse-engineer from test cases) |
| Git history | Reason for code changes, timeline | Medium (requires personal inference) |

---

## 4. FAQ

### Q: The codebase is too large for one week. What to do?

Analyze core modules by priority. Produce panorama + docs for the top 2-3 core modules. Add remaining modules to the backlog and fill in as needed.

### Q: AI analysis results are inaccurate. What to do?

All AI analysis results need human review. Focus on:
- Whether business rule inferences are reasonable
- Whether data flows are complete
- Whether low-confidence items are properly annotated

Mark inaccurate parts as 🟡 or 🔴 for later confirmation.

### Q: No one on the team understands the system. What to do?

Rely entirely on AI analysis + Git history inference:
1. AI analyzes code logic (usually accurate at the factual level)
2. Git log/blame to understand change history and reasons
3. Reverse-engineer business expectations from test cases
4. Mark all inferences as 🟡 Medium Confidence
5. Clearly note in documentation "Not yet human-confirmed"

### Q: Lots of existing docs but unsure if accurate?

Prioritize documentation → code reverse validation (knowledge-sync Skill's "reverse validation" feature). First learn which docs are accurate and which are outdated, then decide whether to update or rewrite.

### Q: How to keep documentation updated going forward?

Establish three mechanisms:
1. **Pre-commit hooks**: Remind to check docs when code changes
2. **Monthly audit**: AI automatically scans code vs. doc differences
3. **MR checklist**: Check documentation sync during review

See `knowledge-sync` Skill for details.

---

## 5. Effectiveness Assessment

After completing code archaeology, evaluate results with these metrics:

| Metric | Target | Measurement Method |
|--------|--------|--------------------|
| Documentation coverage | Core modules 100%, others ≥ 60% | Module count × doc completeness |
| High-confidence entry ratio | ≥ 70% | (🟢 + 🔵 count) / total count |
| API doc completeness | 100% endpoint coverage | Entry point map vs. API docs |
| Newcomer onboarding time | From 2-3 days to half a day | Feedback from new members after reading docs |
| Doc staleness rate | < 10% outdated docs in monthly audit | Audit report |

---

## Appendix: Tool List

| Tool | Purpose | Cost |
|------|---------|------|
| Claude Code | Execute prompts, analyze code, generate docs | $20/month+ |
| TongYi Lingma | Alternative for code analysis | Free |
| Git | Code history analysis | Free |
| Mermaid | Architecture and flow diagram rendering | Free (VS Code plugin) |
| Confluence | Document publishing and collaboration | Already available |
