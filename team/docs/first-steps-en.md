# First Steps Action Checklist (Practical Edition)

> A plan the tech lead can execute alone.
> No team buy-in required. No budget approval needed. Start today.

---

## Day 1: Install Tool + Run Through Flow (1-2 hours)

### 1. Install AI Coding Tool

**Recommended: TongYi Lingma (free, zero barrier)**

- VS Code: Search "TongYi Lingma" in extension marketplace, click install
- JetBrains: Search "TongYi Lingma" in plugin marketplace, click install
- Login after install (Alipay/DingTalk scan code)

**Verify**: Open any code file, type a comment `// Implement: [feature description]`, see if AI gives suggestions. If it does, it's installed correctly.

### 2. Run Through One Complete Flow

Pick the simplest function in the project (pure computation, no external dependencies preferred):

```
Steps:
1. Open the file
2. Select the function code
3. Tell AI: "Generate unit tests for this function, covering normal input, edge cases, and error input"
4. Place generated test code in tests/ directory
5. Run tests
6. Record: from start to all tests passing, how long did it take
```

**Expected**: 30-60 minutes for first complete flow (including摸索 time).

### 3. Record Baseline Data (5 minutes)

| Metric | Today's Value | How to Measure |
|--------|--------------|----------------|
| Project test coverage | ______ % | Run `npm test -- --coverage` or `mvn test` |
| Estimated time to write tests manually for this function | ______ min | Experience-based estimate |
| Actual time with AI | ______ min | Just recorded |
| Can AI-generated tests be used directly? | Yes / No (how much needs changing) | Actual assessment |

---

## Day 2-3: Generate CLAUDE.md with AI (2-3 hours)

### Core Operation: Don't hand-write it, let AI analyze the code

In the project root, use TongYi Lingma's Agent mode (or Claude Code):

```
Prompt (copy and use directly):

Analyze the current project's code and generate a CLAUDE.md file.

Steps:
1. Scan src/ or app/ directory, list actual modules and their responsibilities
2. Read package.json / pom.xml / build.gradle / requirements.txt to determine tech stack
3. Randomly read 5-10 source files, summarize the project's actual naming conventions
   - Variable naming: camelCase / snake_case?
   - Function naming: verb-first or noun-first?
   - File naming: kebab-case / PascalCase / camelCase?
4. Find the project's error handling approach (look at catch blocks, error classes, response formats)
5. Find the project's test framework and test directory structure
6. If CI/CD config files exist (.gitlab-ci.yml / Jenkinsfile / .github/workflows/), analyze the deployment process

Generate CLAUDE.md based on the above. Don't give me a template; give me results from actual code analysis.
```

**Review checklist**:

- [ ] Tech stack description matches package.json/pom.xml
- [ ] Directory structure matches actual code directories
- [ ] Naming conventions reflect how most code actually looks (not how you wish it looked)
- [ ] Common commands are real, executable commands
- [ ] No `[fill in]` placeholders

---

## Day 4-5: Generate All Skills with AI (2-3 hours)

### Core Operation: Have AI batch-generate Skills from CLAUDE.md

```
Prompt (copy and use directly):

Based on the following project info, generate Claude Code Skill files.

Project info:
[Paste the CLAUDE.md you generated]

Team status:
- 5-8 person backend team, low test coverage
- Uses [GitLab/GitHub] for code management
- Uses [JIRA/Zentao] for bug tracking
- Deployment process has many manual steps

Skills to generate:

1. .claude/skills/new-api.md
   - Based on our actual [framework name]
   - Include a real API example (e.g., "create user" endpoint), from Spec to complete code
   - Including route definition, Controller, Service, Repository, unit tests

2. .claude/skills/fix-bug.md
   - Include a real bug example (e.g., "null pointer exception"), showing full fix flow

3. .claude/skills/generate-tests.md
   - Based on our actual [test framework]
   - Include a real function and its generated test example

4. .claude/skills/code-review.md
   - List common issue patterns specific to our project

5. .claude/skills/deploy.md
   - Based on our actual CI/CD config

6. .claude/skills/refactor-module.md
   - Include an actual module analysis report example

7. .claude/skills/generate-docs.md
   - AI documentation generation, including Confluence publishing approach

Each Skill must:
- Use our project's actual tech stack and frameworks
- Include real code examples (not [fill in] placeholders)
- Have clear, directly executable steps
- Include a "Common Issues" section
```

**Review focus**:

- [ ] Code examples use the project's actual frameworks and syntax
- [ ] Test examples use the project's actual test framework
- [ ] Deploy steps match the project's CI/CD configuration
- [ ] No `[fill in]` placeholders (replaced with real examples)

---

## Week 2: Validate Through Real Work

### Task Selection

Pick 3 real tasks from your current work:

| Task | Corresponding Skill | Expected Output |
|------|-------------------|-----------------|
| Add tests to a legacy module | generate-tests | Coverage from [X]% -> [Y]% |
| Fix a real bug | fix-bug | Bug fix + regression test |
| Develop a new API | new-api | Complete API (with tests and docs) |

### Data Recording Template (fill daily)

```
## [Date] AI-Assisted Development Log

### Task: [task description]

#### Time Comparison
- Manual estimate: [X] hours
- AI-assisted actual: [Y] hours
- Saved: [Z] hours ([Z/X]%)

#### AI Contribution
- Code analysis: AI helped me understand [what]
- Code generation: AI generated [N] lines, I directly adopted [M] lines
- Test generation: AI generated [N] test cases, [M] directly usable
- Acceptance rate: [direct + modified] / [total suggestions] = [N]%

#### AI Mistakes
- [What AI got wrong, how you fixed it]

#### Usefulness: 1-5 scale
- Code generation: [score]
- Test generation: [score]
- Code understanding: [score]
```

### Week 2 Goals

By end of week, you should have:

- [ ] Usage data from 3 real tasks
- [ ] Test coverage improved by 15-30% (pilot module)
- [ ] Identified 3 scenarios where AI excels and 2 where it doesn't
- [ ] A real efficiency comparison dataset

---

## Week 3: Influence the Team (Through Actions, Not Meetings)

### Monday: Show AI in Code Review

Don't say "let's all use AI." Instead, when reviewing others' MRs, attach AI check results:

```
Review comment example:

Regular human review:
- Line 42: This query is inside a loop -- N+1 problem
- Line 88: Missing null check

AI additionally found:
- Line 15: This SQL query doesn't use an index (AI found by analyzing WHERE clause)
- Line 67: Potential concurrency issue (AI analyzed transaction boundaries)

Conclusion: AI helped catch 2 extra issues, but business logic correctness (line 55 amount calculation) still needs human review.
```

### Tuesday-Thursday: Help Colleagues with Pain Points

Wait for someone to complain about "this code is unreadable" or "spent all day debugging this":

```
Script: "Let me try using AI to help you look at this"
Action: Analyze with AI right in front of them, 2-3 minutes to results
Key: Let them experience AI on their own most painful problem
```

### Friday: Informal Sharing

Over lunch, tell 2-3 colleagues about your two weeks of data:

```
"I used AI to generate tests for the order module. Coverage went from 5% to 45%,
 and it only took 3 hours. Would've taken two days before. Though about 30%
 of the generated tests needed manual fixes."
```

**Note**: Share real data, including failures. It's more credible.

---

## Week 4: Summary + Consolidation

### Monthly Summary (15 min, informal)

| Metric | Day 0 | Day 30 | Data Source |
|--------|-------|--------|-------------|
| Test coverage (pilot module) | ____% | ____% | Coverage report |
| My daily coding output | ~____ lines | ~____ lines | git log stats |
| AI code acceptance rate | 0% | ____% | IDE stats |
| Colleagues who tried AI | 0 | ____ | Records |
| Docs published to Confluence | ____ | ____ | Confluence stats |

### Honest Assessment (for yourself)

```
What AI does well:
1. [e.g., test generation, visible coverage improvement]
2. [e.g., analyzing legacy code, quick module understanding]
3. [e.g., generating API docs, saved tons of manual writing]

What AI does poorly:
1. [e.g., complex business logic often wrong]
2. [e.g., generated code sometimes doesn't follow project conventions]

Next month plan:
1. [specific plan]
2. [specific plan]
```

---

## If Team Enthusiasm Is Low

**Core strategy**: Don't promote AI. Promote "saving time."

| Situation | What to do |
|-----------|-----------|
| Nobody wants to try | Don't ask them to. Keep using it yourself, show results in MRs and docs. |
| Someone thinks AI is unreliable | Acknowledge shortcomings, show which scenarios work and which don't. |
| Someone fears being replaced | "AI replaces repetitive work, not you." |
| Leadership doesn't support | Use free tools first, get data, then make the case. Don't ask first. |
| After a month only you are using it | That's fine. Your efficiency has already improved. Keep accumulating data. |

**Ultimate strategy**: Use the time AI saves you for architecture improvements, making your code quality and output visibly above the team average. This is the best "silent promotion."
