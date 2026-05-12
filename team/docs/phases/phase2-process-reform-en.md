# Phase 2: Process Transformation (Weeks 5-12) - Unlocking Efficiency

> Goal: Integrate AI tools into daily workflows and optimize the deployment pipeline.
> Core: Upgrade from "individuals using AI" to "the team using AI."

---

## Phase Goals

| Metric | Target |
|--------|--------|
| Full-team AI tool adoption rate | >= 80% |
| AI code acceptance rate | >= 30% |
| Skills library | >= 5 reusable skills |
| Most time-consuming deployment step | Shortened by >= 30% |
| Legacy code refactoring | 2-4 modules completed |

---

## Week 5-6: Skills Library Development

### Goal

Build a library of reusable AI workflows for the team.

### Action Items

- [ ] Catalog common development scenarios
- [ ] Create/refine skill files for each scenario
- [ ] Team training: how to use and create new skills

### Recommended Skill Priorities

| Priority | Skill | Rationale |
|----------|-------|-----------|
| P0 | generate-tests | Validated effective in Phase 1 |
| P0 | fix-bug | High bug-fix frequency |
| P1 | new-api | High new-feature frequency |
| P1 | code-review | Reduces review burden |
| P2 | refactor-module | Legacy code refactoring |
| P2 | deploy | Reduces deployment errors |

### Skill File Structure

```
.claude/skills/
├── new-api.md          # Create new API endpoint
├── fix-bug.md          # Fix bugs
├── generate-tests.md   # Generate unit tests
├── deploy.md           # Release/deploy
├── code-review.md      # Code review
└── refactor-module.md  # Legacy module refactoring
```

---

## Week 7-8: Spec-Driven Development Practice

### Goal

New feature development adopts the **Spec -> Agent Implementation -> Human Review** workflow.

### Implementation Steps

1. **Select 1-2 new features** as SDD pilot candidates
2. **Write Spec documents** in the `docs/specs/` directory
3. **Use AI agents to generate code** from the Spec
4. **Human review** to confirm functionality and security
5. **Summarize SDD workflow improvements**

### Spec Document Template

```markdown
# [Feature Name] Spec

## Background
[Why this feature is needed]

## Feature Description
[Detailed description of the feature]

## API Design
[RESTful API endpoint definitions]

## Data Model
[Table/model design]

## Non-Functional Requirements
- Performance: [requirements]
- Security: [requirements]
- Compatibility: [requirements]

## Acceptance Criteria
- [ ] [criterion 1]
- [ ] [criterion 2]

## Technical Approach (optional, filled by Agent)
[Implementation details]
```

### SDD Workflow Essentials

```
Developer writes Spec -> AI Agent implements -> Developer reviews -> Run tests -> Merge
        |                      |                      |               |
   Clear requirements    Fast code generation    Quality gate    Safety net
```

---

## Week 9-10: Legacy Code Refactoring Kickoff

### Goal

Apply the "Map First, Modify Later" method to refactor 1-2 modules per month.

### Module Selection Criteria

| Factor | Weight | Description |
|--------|--------|-------------|
| Maintenance frequency | High | Modules changed frequently get priority |
| Bug density | High | Modules with concentrated bugs get priority |
| Business importance | High | Core business modules get priority |
| Refactoring difficulty | Low | Start easy to build confidence |

### Refactoring Cadence

```
Week N:     Select module -> Agent generates module map -> Generate test safety net
Week N+1:   Execute refactoring (small commits) -> Continuous verification -> Refactoring report
Week N+2:   Observation period -> Collect feedback -> Prepare for next module
```

### Reference Methods

- **"Map First, Modify Later"** - Claude Code's legacy code approach
- **Test-first** - Generate tests before refactoring
- **Amazon Q's `/transform`** - Automated upgrade transformation approach

---

## Week 11-12: MCP Integration & Process Automation

### Goal

Configure MCP servers to connect internal tools, enabling AI agents to interact directly with the team's toolchain.

### MCP Server Plan

```json
{
  "mcpServers": {
    "jira": "Connect JIRA - Agent can query/update issues",
    "gitlab": "Connect GitLab - Agent can manage MRs/CI",
    "docs": "Connect internal docs - Agent can search documentation",
    "monitor": "Connect monitoring - Agent can query metrics"
  }
}
```

### Process Automation Targets

Identify the most time-consuming steps in the deployment pipeline and automate them with AI agents:

- [ ] Environment configuration automation
- [ ] Regression test automation
- [ ] Release checklist automation
- [ ] Changelog auto-generation

### Phase 2 Results Check

```markdown
## Phase 2 Results Report

### Skills Library
- Created skills: [N]
- Usage frequency: [data]

### SDD Practice
- Completed specs: [N]
- AI code acceptance rate: [N]%

### Legacy Code Refactoring
- Refactored modules: [N]
- Test coverage improvement: [data]
- Code quality metrics: [data]

### Process Improvements
- Deployment pipeline time change: [data]
- Automated steps: [list]
```

---

## Key Success Factors

1. **Skills must be practical** - Start from real team pain points; don't aim for perfection
2. **SDD is not mandatory** - Pilot with selected features first; expand when results are positive
3. **Don't overcommit on refactoring** - 1-2 modules per month; continuous improvement beats big-bang changes
4. **Integrate MCP incrementally** - Connect the most-used tools first, then expand
5. **Keep sharing** - Maintain weekly sharing sessions to showcase new use cases and results

## Risk Mitigation

| Risk | Mitigation |
|------|------------|
| SDD process increases development time | May be slower initially; choosing the right pilot feature is critical |
| Refactoring introduces new bugs | Test safety net + small commits + continuous verification |
| MCP integration security risks | Least-privilege principle; agent operations require audit logs |
| Team participation declines | Showcase phased results so everyone sees progress |
