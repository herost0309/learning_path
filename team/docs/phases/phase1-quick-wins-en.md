# Phase 1: Quick Wins (Weeks 1-4) - Building Confidence

> Goal: Use AI to solve the most painful problems so the team feels "this is useful."
> Core: Don't aim for full rollout. Let 3-5 people experience the benefits first, then expand.
> For the detailed hands-on plan, see `docs/realistic-implementation-en.md`.

---

## Phase Goals (Based on Public Data Benchmarks)

| Metric | Target | Industry Reference |
|--------|--------|--------------------|
| AI code acceptance rate | >= 20% | Alibaba initial: 22%, GitHub Copilot average: 46-55% |
| Pilot module test coverage | +30-40% | AI test generation is ~5-8x faster than manual |
| Per-person coding speed | +20-30% | GitHub study: coding speed +27%, task completion +56% |
| Legacy code understanding time | -60-80% | AI module analysis: 3-4 hours down to 20-40 min |

---

## Week 1: Tool Deployment + Pilot Launch

### Prerequisites

- [ ] Identify AI pioneer group members (3-5 people)
- [ ] Select an AI coding tool (recommended: TongYi Lingma free / Claude Code paid)
- [ ] Choose 1 most painful legacy module as the pilot
- [ ] Schedule a 1-hour kickoff meeting

### Kickoff Meeting Agenda (1 hour)

1. **Why are we doing this** (10 min)
   - Current team analysis: understaffed, high maintenance overhead on legacy code
   - AI tools don't replace people; they amplify each person's capabilities
   - Goal: reduce repetitive work and let everyone focus on higher-value tasks

2. **Tool Introduction & Demo** (20 min)
   - Demo the AI coding tool's core features
   - Demo the complete workflow of "using AI to generate tests for legacy code"
   - Demo "AI-assisted Code Review"

3. **Pilot Module Discussion** (20 min)
   - Why this module was chosen (high maintenance frequency, many bugs, team's biggest pain point)
   - Pilot approach: AI generates tests first -> AI-assisted understanding -> incremental refactoring
   - Task assignment: each person responsible for 1-2 files in the module

4. **Agreements & Planning** (10 min)
   - Weekly sharing session schedule
   - Communication method during the pilot
   - Week 1 task assignments

### Week 1 Action Items

| Who | What | Output |
|-----|------|--------|
| Everyone | Install AI coding tool | Tool available |
| Everyone | Read CLAUDE.md | Understand team standards |
| Pioneer group | Analyze pilot module with AI | Module analysis report |
| Lead | Create project CLAUDE.md | Standards file ready |

---

## Week 2: Legacy Code Test Generation

### Key Tasks

- [ ] Pioneer group members use the `generate-tests` skill to generate unit tests for the pilot module
- [ ] Human review of AI-generated tests to confirm business logic correctness
- [ ] Run tests and record coverage changes

### Execution Method

```
For each file in the pilot module:
1. Use AI agent to analyze the code
2. Use generate-tests skill to produce tests
3. Human review for correctness
4. Run tests and record results
5. Track coverage changes
```

### Week 2 Checkpoints

- [ ] >= 50% of pilot module files have AI-generated tests
- [ ] Test coverage improved from [current]% to [target]%
- [ ] Team members are more familiar with AI tools

---

## Week 3: AI-Assisted Code Review

### Key Tasks

- [ ] Configure AI Code Review rules (using the `code-review` skill)
- [ ] Trial AI Review on 2-3 MRs
- [ ] Collect feedback and adjust rules

### Configuration Highlights

1. Security rules: SQL injection, XSS, sensitive data leakage
2. Performance rules: N+1 queries, full-table scans
3. Standards rules: validate against CLAUDE.md conventions
4. Test rules: check test coverage

### Week 3 Checkpoints

- [ ] AI Review rules configured
- [ ] At least 3 MRs have used AI Review
- [ ] Accuracy feedback collected on AI Review

---

## Week 4: Summary & Expansion

### Key Tasks

- [ ] Collect Phase 1 quantitative data
- [ ] Pioneer group sharing session (present results to full team)
- [ ] Plan Phase 2 rollout

### Sharing Session Agenda (45 min)

1. **Data Presentation** (15 min)
   - Test coverage changes
   - AI code acceptance rate
   - Estimated time saved

2. **Case Demos** (20 min)
   - Demo the complete "AI generates tests for legacy code" process
   - Demo the "AI-assisted Review" experience
   - Share pitfalls encountered and solutions

3. **Q&A and Discussion** (10 min)
   - Address concerns
   - Collect interest and needs from other team members

### Phase 1 Results Template

```markdown
## Phase 1 Results Report

### Quantitative Data
| Metric | Baseline | Current | Change |
|--------|----------|---------|--------|
| AI tool usage rate | 0% | [N]% | +[N]% |
| Pilot module test coverage | [N]% | [N]% | +[N]% |
| AI code acceptance rate | N/A | [N]% | New |
| AI Review usage count | 0 | [N] | New |

### Qualitative Feedback
- [Pioneer member A's feedback]
- [Pioneer member B's feedback]

### Issues Encountered
- [Issue and resolution]

### Next Steps
- Phase 2 launch plan
```

---

## Key Success Factors

1. **Choose the "most painful" module** - Make the impact obvious
2. **Pioneer members must be enthusiastic** - Select people interested in new technology
3. **Don't force adoption** - Let results speak first, then expand naturally
4. **Share results quickly** - Don't wait for "perfect"; share progress as it happens
5. **Tolerate imperfection** - AI-generated tests/reviews may not be perfect; building the habit is key

## Risk Mitigation

| Risk | Mitigation |
|------|------------|
| Pioneer members not engaged | 1-on-1 conversation to understand reasons; replace if necessary |
| AI tool underperforms expectations | Adjust use cases to find the best fit |
| Team anxiety about "being replaced" | Emphasize "AI amplifies humans" positioning; showcase work value enhancement cases |
| Security/compliance concerns | Confirm AI tool's data handling policy; use on-premise deployment if necessary |
