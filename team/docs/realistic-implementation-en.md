# Realistic Implementation Plan: One Person Driving Team-Wide AI Transformation

> An operations manual for the team technical lead.
> No theory -- just how to do it, what results to expect, and how to handle problems.

---

## 1. Let's Be Honest: How Bad Is It Really

Take a typical 5-8 person backend team. Here are the real numbers I see most often:

### Typical Team Profile

| Dimension | Data | Source |
|-----------|------|--------|
| Team size | 5-8 people (4-6 actually writing code) | Multiple small-team surveys |
| Daily legacy code maintenance | 40-60% (~3-4 hours/day) | Developer self-reports |
| Test coverage | < 20%, core modules near 0% | SonarQube scans |
| Monthly production bugs | 10-20, of which 2-3 are P0/P1 | JIRA/Zentao statistics |
| Time per deployment | 2-4 hours (including env config, manual regression) | CI/CD statistics |
| Code review quality | Goes through the motions; most get "LGTM" | MR review records |
| Technical documentation | Almost none, or outdated if it exists | Confluence last-updated timestamps |
| AI tool usage | A few people tried it privately, no organizational push | Survey |

**Core contradiction**: Want to improve -> No time to improve -> Keep stacking code on bad code -> Code gets worse.

### Real Data from Other Teams (Not PPT Numbers)

| Company/Team | Metric | Data | Source |
|--------------|--------|------|--------|
| GitHub (Copilot) | Code acceptance rate | 46% (first suggestion) - 55% (after adjustment) | GitHub Octoverse 2024 |
| GitHub (Copilot) | Efficiency gain | Coding speed +27%, task completion +56% | GitHub + Forrester study |
| Google | AI-generated code share | 25%+ of new code AI-assisted | 2024 Q2 earnings call |
| Microsoft (internal) | Developer efficiency gain | +29.8% | Microsoft internal study (published in Nature) |
| JetBrains survey | Developer AI usage | 77% of developers using AI tools | 2024 Developer Survey |
| Stack Overflow survey | Developer sentiment | 76% using or planning to use AI | 2024 Developer Survey |
| Alibaba (TongYi Lingma) | Acceptance rate trajectory | 22% (initial) -> 33% (3 months) -> 60%+ (heavy users) | Alibaba Tech Blog |
| Tencent (CodeBuddy) | Cross-file refactoring success | 92% | Tencent Tech Conference |
| GitClear | AI code quality | AI-assisted code has 1.4x higher churn rate | GitClear 2024 Report |

**Key takeaways**:
- Going from 0% to 30% AI code acceptance takes 2-4 weeks
- Real efficiency gains are 20-30%, not 10x
- AI-generated code quality needs human oversight (higher churn rate)
- The tool is never the bottleneck -- **people** are

---

## 2. How One Person Can Do It: Core Strategy

### Strategy: Build the Tools First, Then Let People Use Them

The core idea is not "convince everyone to use AI." It is:

1. **You build all the Skills yourself** (using AI to help you write them)
2. **You use them in your own work and show results**
3. **Put the data in front of everyone**
4. **Give them a "ready-to-use" toolkit that lowers the barrier to near zero**

```
Traditional approach (high failure rate):
  Kickoff meeting -> Training -> Hope people use it -> Gradual abandonment

My approach (one person drives it):
  I build it first -> I use it well -> Show data -> Give everyone "foolproof" tools
```

### Your Role Transformation

```
Before: Code writer
After: Spec writer + Code reviewer
Key insight: You're not "promoting AI" -- you're "increasing your own output." Others join naturally.
```

---

## 3. 30-Day Detailed Plan (One Person Executes)

### Prerequisites

- You (tech lead) can invest 2-3 hours/day on this
- Team has 5-8 developers
- Project uses Git (GitLab/GitHub)
- You have permission to install IDE plugins

### Cost

| Tool | Cost | Recommendation |
|------|------|----------------|
| TongYi Lingma | Free | Best starting point, zero cost |
| Claude Code | $20/month (Pro Plan) | Upgrade when you have budget |
| Cursor | $20/month | If the team prefers a standalone IDE |

> **Critical**: Don't ask for budget upfront. Use free tools first, get results, then use data to justify spend.

---

### Week 1: Infrastructure Setup (You Do It Alone)

**Goal**: Build all tools and documents, laying the foundation for the next 3 weeks.

#### Day 1-2: Environment Setup

- [ ] Install TongYi Lingma (search in VS Code extension marketplace)
- [ ] Run through one complete flow: pick a simple file, have AI generate tests -> run -> pass
- [ ] Record the time: from installation to first passing test, how long did it take?

**Actual time reference**: First install + complete flow, about 1-2 hours.

#### Day 3-4: Write CLAUDE.md

**Don't start from a template** -- start from your actual project:

```bash
# Use AI to help you generate CLAUDE.md
# Run the following prompt in the project root (for TongYi Lingma Agent mode or Claude Code):
```

**Prompt example**:

```
Analyze the current project's codebase and generate a CLAUDE.md file for me.

Requirements:
1. Scan the src/ directory and list actual modules with their responsibilities
2. Read package.json / pom.xml / requirements.txt to determine the tech stack
3. Find the project's actual naming conventions (look at how existing code names things)
4. Find the project's error handling approach (look at catch blocks, error classes, response formats)
5. Find the project's test framework and test directory structure
6. Based on the above, generate CLAUDE.md

Don't give me a template. Give me results based on actual code analysis.
```

**Key point**: Use AI to analyze real code to generate standards, not hand-written templates. This way CLAUDE.md reflects reality.

**Expected output**:
- A CLAUDE.md based on actual project code analysis
- Contains real tech stack, real directory structure, real naming conventions
- Contains NO `[fill in]` placeholders

#### Day 5: Write All Skills (Using AI to Help You)

**Key operation**: You don't need to hand-write each Skill. Use AI to generate them:

```
Based on the following project information, generate Claude Code Skill files for me.

Project info (from CLAUDE.md):
[Paste your generated CLAUDE.md content]

Team status:
- 5-8 person backend team
- Test coverage < 20%
- Uses [GitLab/GitHub] for code management
- Uses [JIRA/Zentao] for bug tracking

Skills to generate:
1. new-api.md - Create new API endpoint (based on our actual framework)
2. fix-bug.md - Fix bugs (include a real bug example)
3. generate-tests.md - Generate unit tests (based on our actual test framework)
4. code-review.md - Code review
5. deploy.md - Release/deploy (based on our actual CI/CD)
6. refactor-module.md - Legacy code refactoring
7. generate-docs.md - AI documentation generation with Confluence publishing

Each Skill must:
- Use our project's actual tech stack in code examples
- No [fill in] placeholders -- use real example data
- Include one complete example (e.g., a real API from Spec to full code)
```

**Expected time**: 2-3 hours (including reviewing and adjusting AI-generated Skills).

#### Week 1 Checkpoint

| Checkpoint | Done? |
|------------|-------|
| TongYi Lingma installed and working | |
| Generated tests for 1 file using AI | |
| CLAUDE.md generated from actual code analysis (no placeholders) | |
| 7 Skill files generated | |
| Total time spent <= 15 hours | |

---

### Week 2: Validate Through Real Work

**Goal**: Complete at least 2 real development tasks using AI + Skills, collect real data.

#### Real Task 1: Generate Tests for a Legacy Module (using generate-tests Skill)

```
Steps:
1. Pick a frequently-maintained module (e.g., orders/payments/users)
2. Use AI to analyze this module, record analysis time
3. Use generate-tests Skill to produce tests
4. Run tests, fix failures
5. Record coverage changes
```

**Real Data Record** (this is key for convincing the team later):

| Step | Manual Time | AI-Assisted Time | Savings |
|------|------------|-----------------|---------|
| Analyze module (read code, understand logic) | 2-4 hours | 20-40 min | 70-80% |
| Write tests (20+ cases) | 4-8 hours | 40-90 min | 75-85% |
| Debug tests (fix failures) | 1-2 hours | 30-60 min | 50-60% |
| **Total** | **7-14 hours** | **1.5-3 hours** | **~75%** |

> Note: This is efficiency gain for test generation. Daily coding efficiency gain is typically 20-30%.

#### Real Task 2: Fix a Bug (using fix-bug Skill)

Pick a real bug, record:

```
Bug description: [actual bug]
Traditional approach estimated time: [your estimate]
AI-assisted actual time: [actual time]

What AI helped with:
- Root cause analysis: [what AI suggested, was it correct?]
- Test generation: [quality assessment]
- Fix code generation: [directly usable?]
```

#### Week 2 Data Summary

By end of week, you should have:

```markdown
## My AI-Assisted Development Data (Week 2)

### Task 1: [module name] test generation
- Manual estimate: [X] hours
- AI-assisted actual: [Y] hours
- Efficiency gain: [Z]%
- Test coverage: [old]% -> [new]%
- AI code acceptance rate: [N]%

### Task 2: [bug description]
- Manual troubleshooting estimate: [X] hours
- AI-assisted: [Y] hours
- Root cause: AI got it right on attempt #[N]

### Overall impressions
- Most useful scenario: [specific scenario]
- Least useful scenario: [specific scenario]
- AI mistakes: [specific errors]
```

---

### Week 3: Get the Team Onboard (Through Actions, Not Meetings)

**Goal**: Not training, but letting people naturally encounter AI in daily work.

**Core principle**: Don't say "come learn AI." Say "I built a tool, try it, it saves time."

#### Monday: Show AI's value in Code Review

- When you review others' MRs, attach an AI review report
- Don't make it mandatory -- just "ran AI check while I was at it"
- Tag comments: `[AI Found]` vs `[Human Found]` so people see the difference

#### Tuesday-Thursday: Help a colleague solve a real problem

Wait for someone to complain about "this code is unreadable" or "spent all day debugging this."

```
Script: "Let me try using AI to help you look at this"
Action: Analyze with AI right in front of them, 2-3 minutes to results
Key: Let them experience AI on their own most painful problem
```

#### Friday: Share your data (informally)

Over lunch, tell 2-3 colleagues about your two weeks of data:

```
"I used AI to generate tests for the order module. Coverage went from 5% to 45%,
 and it only took 3 hours. Would've taken two days before. Though about 30%
 of the generated tests needed manual fixes -- it's not magic."
```

---

### Week 4: Consolidate + Summarize

**Monthly summary (15 min, informal)**:

```markdown
## One Month of AI-Assisted Development Summary

### Data
| Metric | Day 0 | Day 30 | Change |
|--------|-------|--------|--------|
| Test coverage (pilot module) | [N]% | [N]% | +[N]% |
| My daily coding output | ~[N] lines | ~[N] lines | +[N]% |
| AI code acceptance rate | 0% | [N]% | +[N]% |
| Colleagues who tried AI | 0 | [N] | +[N] |
| Docs published to Confluence | [N] | [N] | +[N] |

### Honest assessment
- What AI does well: [list]
- What AI does poorly: [list]
- Scenarios to keep using: [list]
- Scenarios not suited yet: [list]
```

---

## 4. Handling Low Team Enthusiasm

### Common Negative Reactions and Responses

| Reaction | Real reason | How to respond |
|----------|------------|----------------|
| "No time to learn new things" | Not lack of time, lack of perceived value | Don't ask them to "learn" -- help them "use once" |
| "AI-generated code is unreliable" | It sometimes is | Acknowledge this; show which scenarios are reliable |
| "Is this going to replace us?" | Fear of the unknown | "AI replaces repetitive work, not you. Your value is in business understanding and architecture design." |
| "I'm used to my own way" | Inertia | Don't ask them to change habits; just offer an additional option |
| "Leadership doesn't support it" | This may be true | Use free tools first, get data, then make the case |

### If After One Month You're Still the Only One Using It

**That's fine.** You've already improved your own efficiency by 20-30%. Keep using it, keep collecting data. Organizational change doesn't happen in a month.

But you can do one thing: **Make your AI-assisted work visible**.

- Your MRs are tagged `[AI-Assisted]`
- Your test coverage keeps going up
- Your bug fix speed is faster than others
- You have time for architecture improvements

This is "silent promotion" -- more effective than any meeting.

---

## 5. Before vs. After (Expected Data)

### Conservative Estimates (Based on Public Data + Practical Experience)

| Metric | Before (Day 0) | After (Day 30) | Change | Basis |
|--------|----------------|-----------------|--------|-------|
| Test coverage (pilot module) | 5-15% | 40-60% | +30-45% | AI test generation is 5-8x faster |
| Daily code output per person | ~200 lines | ~280 lines | +40% | GitHub: coding speed +27%, plus test gen +75% |
| Bug fix time | 2-4 hours | 1-2 hours | -50% | AI-assisted root cause + test gen |
| New API development time | 1-2 days | 0.5-1 day | -40% | SDD: Spec -> code -> review |
| Code review quality | Going through motions | Catches 30-50% of common issues | +30-50% | AI auto-scan + human deep review |
| Legacy code understanding time | 2-4 hours/module | 20-40 min/module | -80% | AI module analysis + code explanation |

---

## 6. Using AI to Generate Documentation

### 6.0 Taking Over Unknown Codebases: Code Archaeology

If your team has taken over a codebase with missing documentation (or severely outdated docs), you need to **understand the code** before generating documentation. This is "code archaeology" — using an AI Agent to reverse-analyze code, extracting business logic and architecture design.

**Typical scenarios**:
- Taking over a legacy system where previous developers have left
- Codebase has severely missing or outdated documentation
- Cross-team collaboration requiring quick understanding of another system
- New hires need to quickly get familiar with the project

**Approach**:

1. **Global scan** (1 day): Use AI to scan the entire codebase, producing a project panorama, entry point map, and dependency graph
2. **Module deep dive** (2-3 days): Analyze core modules one by one, extracting business rules, data flows, and state transitions
3. **Knowledge output** (1 day): Transform analysis results into a maintainable document set

For detailed step-by-step instructions, see `docs/code-archaeology-guide-en.md` (Code Archaeology Practical Guide), used together with the `.claude/skills/code-archaeology-en.md` Skill.

Generated documentation uses a **confidence system** to annotate the reliability of each piece of information (🟢 High / 🟡 Medium / 🔴 Low / 🔵 Human Confirmed). For details, see `docs/confidence-system-en.md`.

After documentation is complete, use the `knowledge-sync` Skill to establish a long-term sync mechanism, ensuring documentation stays consistent with code.

### 6.1 Why Let AI Generate Docs

Technical documentation is one of the biggest team pain points:

| Problem | How AI Helps |
|---------|-------------|
| No time to write docs | AI scans code and auto-generates |
| Docs become outdated | When code changes, AI regenerates |
| Inconsistent format | Skills enforce consistent output format |
| Nobody wants to write | AI writes the first draft, humans review |

### 6.2 Confluence Integration Strategies

**Three-step approach**:

**Step 1 (Week 1)**: AI generates Markdown -> manually paste into Confluence
- Zero configuration, works immediately
- One person can document all major module APIs in one day

**Step 2 (Week 2-3)**: Write automation scripts for semi-auto publishing
- Use AI to write a Python/Node.js script
- Script reads AI-generated Markdown, publishes via Confluence API
- Add "auto-update docs after release" to CI/CD pipeline

**Step 3 (Week 4+)**: MCP Server for full automation
- Configure Confluence MCP Server for Claude Code
- Code changes trigger document updates automatically

### Confluence Page Structure

```
Space: DEV
├── Project Overview (home page)
├── API Documentation/
│   ├── User Module API
│   ├── Order Module API
│   └── Payment Module API
├── Architecture Documentation/
│   ├── System Overview
│   ├── Module Responsibilities
│   └── Database Design
├── Operations Manual/
│   ├── Deployment Process
│   └── Incident Response
└── AI Practices/
    ├── Weekly AI Usage Reports
    ├── Prompt Tips Collection
    └── Lessons Learned
```

### Tagging AI-Generated Docs on Confluence

| Label | Meaning |
|-------|---------|
| `auto-generated` | AI-generated, not yet human-reviewed |
| `ai-reviewed` | AI-generated + human-reviewed |
| `manually-written` | Pure human-written |
| `outdated` | Needs update (flagged by AI audit) |

---

## 7. Risk List (No-Nonsense Version)

| Risk | Probability | Impact | Practical Mitigation |
|------|-------------|--------|---------------------|
| Team completely uninterested | High | Medium | Use it yourself. Don't force others. Let output speak. |
| AI-generated code has bugs | High | Medium | All AI code must be reviewed. AI is an assistant, not the author. |
| Company forbids code sharing externally | Medium | High | TongYi Lingma has enterprise on-premise version. Confirm before use. |
| AI tools unstable/rate-limited | Medium | Low | Prepare 2 tools. TongYi Lingma + GitHub Copilot. |
| Leadership doesn't support | Medium | High | Use free tools first, get data, then make the case. Don't ask permission first. |
| No visible results in one month | Low | High | If even you see no benefit, reevaluate tool selection. |

---

## 8. Key Principles

1. **One person can do it** -- No need to wait for the team to be ready
2. **Use free tools** -- No need to wait for budget approval
3. **Let data speak** -- No need to convince anyone
4. **Help at their pain points** -- No need for training sessions
5. **Accept imperfection** -- AI isn't perfect, but it's better than nothing
6. **Keep going** -- If one month isn't enough, do two. The key is to start.
