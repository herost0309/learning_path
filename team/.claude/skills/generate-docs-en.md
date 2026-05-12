# Skill: AI-Generated Technical Documentation

> Purpose: Use AI to automatically generate technical documentation from code, solving the "nobody writes docs" problem.
> This is one of the few "zero-controversy" AI use cases -- nobody likes writing docs, but everyone needs them.

## Trigger

- New feature completed, needs documentation
- Legacy module lacks documentation
- API endpoints need documentation generated/updated
- Architecture docs needed
- Code changed and docs need syncing
- Publishing documentation to Confluence

## Input Requirements

- **Document type**: API docs / Architecture docs / Module description / Ops manual / Changelog
- **Target scope**: Code path or module to document
- **Output location**: Local Markdown / Confluence page / Code comments
- **Audience**: New team members / Internal team / External partners

## Execution Steps

### Step 1: Determine Document Type and Template

| Document Type | Location | Typical Audience |
|---------------|----------|-----------------|
| API documentation | `docs/api/` | Frontend, QA, external partners |
| Module architecture | `docs/architecture/` | New team members, cross-team collaboration |
| Changelog | `CHANGELOG.md` | Entire team |
| Operations manual | `docs/ops/` | Ops, on-call staff |
| Spec document | `docs/specs/` | Developers, product managers |

### Step 2: AI Code Analysis

Use different prompts based on document type:

#### Generate API Documentation

```
Prompt:
Analyze the following route/Controller files and generate API documentation.

Requirements:
1. List all HTTP endpoints (method, path, description)
2. List request parameters for each endpoint (path params, query params, request body)
3. List response formats (success and error)
4. Note authentication requirements
5. Note if the endpoint is paginated

File path: [paste path]
```

#### Generate Module Architecture Documentation

```
Prompt:
Analyze the src/modules/[module-name]/ directory and generate module architecture documentation.

Requirements:
1. One-sentence description of the module's responsibility
2. List the module's public interfaces (functions/classes/APIs)
3. Map the data flow (input through components to output)
4. List external services/databases the module depends on
5. Flag high-complexity or high-risk functions
6. Suggest optimization directions

Output format: Markdown
```

#### Generate Changelog

```
Prompt:
Based on the following git commit history, generate CHANGELOG entries.

Rules:
- feat: categorized under "Features"
- fix: categorized under "Bug Fixes"
- refactor: categorized under "Refactoring"
- breaking change: flagged separately as "BREAKING CHANGES"
- Write one human-readable sentence per commit (don't copy commit messages verbatim)
- Order by recency (newest first)

Commit history:
[paste git log output]
```

### Step 3: Human Review

AI-generated documentation **must** be reviewed for:

- [ ] **Accuracy**: Did AI correctly understand the code logic?
- [ ] **Completeness**: Are there missing endpoints/parameters/scenarios?
- [ ] **Currency**: Does it reflect the latest state of the code?
- [ ] **Clarity**: Are descriptions clear and unambiguous?
- [ ] **Security**: Does it leak sensitive information (keys, internal IPs)?

> Most common AI doc issues: correct descriptions but missing edge cases; parameter lists missing optional params.

### Step 4: Publish to Confluence

#### Method A: Manual Paste (Simplest)

1. AI generates Markdown format documentation
2. Create a new page in Confluence
3. Paste Markdown content (Confluence auto-converts to rich text)
4. Add page labels (e.g., `api-doc`, `auto-generated`)

#### Method B: Automated Script Publishing

```python
# Generate this script with AI, fill in your Confluence credentials
# pip install requests markdown

import requests
import markdown

def publish_to_confluence(title, markdown_content, space_key, parent_id=None):
    """Publish a Markdown document to Confluence."""
    html_content = markdown.markdown(markdown_content)

    url = "https://your-company.atlassian.net/wiki/rest/api/content"
    headers = {
        "Authorization": "Bearer YOUR_API_TOKEN",
        "Content-Type": "application/json"
    }

    payload = {
        "type": "page",
        "title": title,
        "space": {"key": space_key},
        "body": {
            "storage": {
                "value": html_content,
                "representation": "storage"
            }
        }
    }

    if parent_id:
        payload["ancestors"] = [{"id": parent_id}]

    response = requests.post(url, json=payload, headers=headers)
    return response.json()

# Usage example
publish_to_confluence(
    title="Order Module API Documentation",
    markdown_content=open("docs/api/orders.md").read(),
    space_key="DEV"
)
```

#### Method C: MCP Server (Claude Code)

After configuring the Confluence MCP Server:

```
"Analyze the code in src/modules/orders/ and generate API documentation.
 Publish it to the DEV space in Confluence with the title 'Order Module API Documentation'."
```

### Step 5: Documentation Update Strategy

| Scenario | Update Method | Trigger |
|----------|--------------|---------|
| New endpoint added | AI re-scans route files, incremental update | Every release |
| Endpoint parameter changed | AI diffs changes, updates docs | On MR merge |
| Module refactored | AI re-analyzes module, overwrites docs | After refactoring completes |
| Periodic audit | AI compares code vs docs, flags stale sections | Monthly |

**Confluence page maintenance** -- add this banner to auto-generated pages:

```html
<div style="background: #f0f0f0; padding: 8px; border-radius: 4px;">
  <strong>Auto-generated by AI</strong> |
  Generated: 2026-05-12 |
  Based on commit: abc1234 |
  <a href="#">Regenerate</a>
</div>
```

## Confluence Best Practices

### Recommended Page Structure

```
Confluence Space: DEV
├── Project Overview (home page)
│   ├── Tech stack description
│   ├── Repository and branch strategy
│   └── Environment URL list
├── API Documentation
│   ├── API Doc Template (universal template page)
│   ├── User Module API
│   ├── Order Module API
│   └── Payment Module API
├── Architecture Documentation
│   ├── System architecture overview
│   ├── Module responsibilities
│   ├── Database design
│   └── Third-party service integrations
├── Operations Manual
│   ├── Deployment process
│   ├── Environment configuration
│   └── Common incident response
└── AI Practices
    ├── Weekly usage reports
    ├── Prompt tips collection
    └── Efficiency data dashboard
```

### Three-Step Confluence Rollout

**Step 1 (Week 1)**: AI generates Markdown -> manual paste to Confluence
- Zero configuration, works immediately
- One person can document all major module APIs in one day

**Step 2 (Week 2-3)**: Write automation script for semi-auto publishing
- Use AI to write a Python/Node.js script
- Script reads AI-generated Markdown and publishes via Confluence API
- Add "auto-update docs after release" step to CI/CD

**Step 3 (Week 4+)**: MCP Server for full automation
- Configure Confluence MCP Server for Claude Code
- Code changes trigger document updates

### Confluence AI Document Labels

| Label | Meaning |
|-------|---------|
| `auto-generated` | AI-generated, not yet human-reviewed |
| `ai-reviewed` | AI-generated + human-reviewed |
| `manually-written` | Pure human-written |
| `outdated` | Needs update (flagged by AI audit) |

## Code Archaeology Scenario

When facing a legacy codebase with missing documentation, use the `generate-docs` Skill together with the `code-archaeology` Skill:

1. **First use code-archaeology to understand the code**: Global scan → Module deep dive → Extract business rules
2. **Then use generate-docs to produce documentation**: Based on understanding results, generate standardized docs

Typical workflow:

```
# Workflow for filling in documentation for unknown modules

## Step 1: Analyze the module with code-archaeology
Use code-archaeology Skill Phase 2 for deep-dive analysis of the target module,
producing business rules tables and data flow diagrams.

## Step 2: Generate standardized documentation with generate-docs
Based on Step 1 analysis results, use this Skill to generate:
- Module architecture documentation
- API documentation
- Data model documentation

## Step 3: Annotate confidence levels
Annotate all generated content with confidence levels (see confidence-system).
```

## Existing Documentation Integration

When the codebase already has partial documentation (but may be outdated or incomplete):

### Integration Strategy

```
Prompt:
The following documents already exist in the project. Please integrate them into the new documentation system.

Existing document list:
[List existing doc paths and brief content descriptions]

Requirements:
1. Read each existing document's content
2. Validate accuracy against current code
3. Preserve still-accurate content, annotating source as "existing document"
4. Update outdated sections, annotating changes
5. Fill in missing sections, annotating source as "AI analysis"
6. Annotate each item with confidence level

Output: Complete integrated documentation
```

### Difference Handling

| Difference Type | Handling |
|----------------|----------|
| Existing doc matches code | Preserve original text, annotate 🟢 |
| Existing doc partially outdated | Update outdated parts, keep rest, annotate 🟡 |
| Existing doc completely outdated | Regenerate from code, note in document |
| Feature described in docs no longer exists | Mark as "deprecated," don't delete (preserve history) |
| AI finds new feature without documentation | Add documentation, mark as "AI-added" |

## Confidence Annotation

Generated documentation should use the confidence system defined in `confidence-system`:

| Marker | Meaning | Usage in Documentation |
|--------|---------|----------------------|
| 🟢 High Confidence | AI confirmed from code | API paths, parameter types, etc. that can be extracted directly from code |
| 🟡 Medium Confidence | AI inferred, needs confirmation | Business rules, state transitions requiring business context understanding |
| 🔴 Low Confidence | AI cannot determine | Complex async logic, legacy magic numbers, etc. |
| 🔵 Human Confirmed | Verified by human | Content confirmed by the team |

When generating documentation, add confidence annotations for sections involving business logic and design decisions. For detailed specifications, see `docs/confidence-system-en.md`.

## Notes

- AI-generated docs are **first drafts** -- they must be human-reviewed before becoming official
- API docs commonly miss: error codes, pagination parameters, authorization requirements
- Architecture docs commonly get wrong: data flow direction, async call chains, caching strategies
- Run monthly AI audits to check documentation-code consistency (see `knowledge-sync` Skill)
- Clearly mark auto-generated Confluence pages to avoid misleading readers
- For unknown codebases, use the `code-archaeology` Skill first to understand the code, then use this Skill to generate documentation
