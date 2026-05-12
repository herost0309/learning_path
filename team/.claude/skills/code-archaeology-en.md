# Skill: Code Archaeology

> Purpose: Systematically reverse-analyze unknown or partially known codebases, extracting business logic, architecture design, and implementation details to fill in missing technical documentation.
> Use cases: Taking over legacy systems, knowledge gap from team attrition, filling documentation gaps.

## Trigger Conditions

- Taking over a legacy codebase with no documentation
- Key team member left, need to quickly understand their modules
- Need to fill in complete technical documentation for an existing codebase
- Existing documentation is severely outdated and needs re-documentation
- Need to quickly understand another team's system for cross-team collaboration

## Three-Phase Method Overview

```
Phase 1: Global Scan      →  Produce project panorama
Phase 2: Module Deep Dive  →  Extract business rules and data flows per module
Phase 3: Knowledge Output  →  Generate maintainable document set
```

---

## Phase 1: Global Scan

**Goal**: Build a holistic understanding of the entire codebase in minimal time.

### 1.1 Project Panorama

```
Prompt:
Analyze the current project's complete codebase and generate a project panorama.

Requirements:
1. Identify the tech stack (framework, language version, build tools, key dependencies)
2. List directory structure with responsibility descriptions
3. List all business modules (inferred from directory names and code)
4. Identify project type (monolith / microservices / serverless / library)
5. Estimate code scale (approximate file count and lines of code)

Output format: Markdown with directory tree and tech stack table
```

**Output Template**:

```markdown
# [Project Name] Panorama

## Tech Stack
| Category | Technology | Version | Purpose |
|----------|-----------|---------|---------|
| Language | ... | ... | ... |
| Framework | ... | ... | ... |
| Database | ... | ... | ... |
| Message Queue | ... | ... | ... |

## Directory Structure
​```
[project root]/
├── [directory] -- [responsibility description]
└── ...
​```

## Module Inventory
| Module | Path | Inferred Responsibility | Estimated Size |
|--------|------|------------------------|----------------|
```

### 1.2 Entry Point Map

```
Prompt:
Scan the project code to find all "entry points" -- locations where external triggers cause the system to execute.

Entry types to identify:
1. HTTP API endpoints (route definitions, Controllers)
2. Scheduled tasks (Cron Jobs, Scheduled Tasks)
3. Message consumers (MQ Listeners, Event Handlers)
4. CLI commands (command-line entry points)
5. External triggers (Webhooks, Callbacks)
6. Main program entry (main function, startup scripts)

For each entry point, list:
- Entry type and path
- Corresponding code file and line number
- Brief functional description (inferred from code)

Output format: Tables grouped by entry type
```

**Output Template**:

```markdown
# [Project Name] Entry Point Map

## HTTP API Endpoints
| Method | Path | File | Function |
|--------|------|------|----------|
| GET | /api/users | src/controller/UserController.java:42 | User list query |

## Scheduled Tasks
| Schedule | File | Function |
|----------|------|----------|
| 0 2 * * * | src/job/DataSyncJob.java | Daily data sync at 2 AM |

## Message Consumers
| Topic/Queue | File | Function |
|-------------|------|----------|
| order.created | src/listener/OrderListener.java | Process order creation events |
```

### 1.3 Dependency Graph

```
Prompt:
Analyze the project's inter-module dependencies and external service dependencies.

Requirements:
1. Inter-module call relationships: which module calls which
2. External service dependencies: third-party APIs or services called
3. Data dependencies: databases, caches, message queues used
4. Shared dependencies: common components shared across modules

Output format:
- Text description of dependencies (A depends on B)
- Direction annotation (unidirectional / bidirectional)
- Type annotation (sync call / async message / shared data)
```

### 1.4 Existing Documentation Inventory

```
Prompt:
Scan all existing documentation files in the project, including:
1. README.md and other Markdown files
2. All files under docs/ directory
3. Code comments (especially file headers and module-level comments)
4. Swagger/OpenAPI definition files
5. Comments in configuration files
6. CHANGELOG, CONTRIBUTING and similar community docs

For each document, assess:
- Content summary
- Last update time (from git log)
- Consistency with current code (is it outdated?)
- Missing critical information

Output format: Table, sorted by document quality
```

---

## Phase 2: Module Deep Dive

**Goal**: Deeply analyze each business module one by one, extracting business rules and data flows.

### 2.1 Code vs. Documentation Cross-Reference

For each module, execute:

```
Prompt:
Analyze the code under [module path] directory and cross-reference it with the following existing documentation:
[Paste existing doc content, or note "No existing documentation"]

Requirements:
1. Summarize the module's actual functionality (inferred from code)
2. Differences from existing documentation (if documentation exists)
3. Features described in docs but absent in code (removed?)
4. Features present in code but not documented (documentation gap)
5. Severity of each difference (Critical / Medium / Low)

Output format:
- Difference list, each with: location, description, severity
```

### 2.2 Extract Business Rules

```
Prompt:
Deep-dive into the code under [module path] directory and extract implicit business rules.

Focus areas:
1. if/else conditional branches -- what business scenario does each branch represent?
2. Enum values and constants -- what is their business meaning?
3. State machine logic -- what states do entities have, how do they transition?
4. Validation rules -- which inputs are rejected and why?
5. Permission checks -- which operations require which roles/permissions?
6. Exception handling -- which error scenarios are handled and how?
7. Calculation logic -- business computation formulas for amounts, discounts, scores, etc.

Annotate each business rule with a confidence level:
- [High] Rule is clear with good naming or comments in code
- [Medium] Rule is inferable but needs human confirmation
- [Low] Rule is uncertain, AI cannot determine business meaning

Output format: Decision table + confidence annotations
```

**Output Template**:

```markdown
# [Module Name] Business Rules

## State Transitions
| Current State | Trigger | Target State | Confidence |
|---------------|---------|-------------|------------|
| Pending Payment | Payment success callback | Paid | [High] |
| Paid | No shipment after 7 days | Auto-cancelled | [Medium] |

## Validation Rules
| Validation | Rule | Error Message | Confidence |
|-----------|------|--------------|------------|
| Phone number | Must be 11 digits | "Invalid phone format" | [High] |
| Order amount | > 0 and <= 999999 | "Amount out of range" | [High] |

## Calculation Logic
| Calculation | Formula/Rule | Confidence |
|------------|-------------|------------|
| Discount | VIP 5% off, SVIP 10% off | [Medium] |
```

### 2.3 Generate Data Flow Diagrams

```
Prompt:
Trace the data flow in [module path] from entry point through storage to response.

For each major function (API endpoint / scheduled task / message handler), map:
1. Where the request comes in
2. Processing steps (function call chain)
3. Which data tables/caches/external services are read or written
4. What data is returned
5. How exceptions are handled

Output format: Text description of call chain, with code locations (file:line) at each step
```

**Output Template**:

```markdown
# [Module Name] Data Flows

## POST /api/orders (Create Order)
​```
Entry point: OrderController.create() [src/controller/OrderController.java:35]
  → Parameter validation: OrderValidator.validate() [src/validator/OrderValidator.java:12]
  → Query product: ProductService.getById() [src/service/ProductService.java:88]
  → Calculate price: PricingEngine.calculate() [src/pricing/PricingEngine.java:23]
  → Check inventory: InventoryService.check() [External: inventory-api]
  → Create order: OrderRepository.save() [Write: orders table]
  → Send notification: NotificationService.send() [Async: MQ order.created]
  → Return: OrderResponse [200 OK]
​```

Exception paths:
- Product not found → 404
- Insufficient inventory → 409
- Price calculation failed → 500
```

### 2.4 Flag Uncertain Items

```
Prompt:
After analyzing the code in [module path], list everything you cannot determine with confidence.

Include but not limited to:
1. Variables/functions with unclear business meaning
2. Old code you're unsure is still in use
3. Global state modifications with unclear side effects
4. Async logic with incomplete call chains
5. Suspicious logic that might be bugs

For each uncertain item, annotate:
- Location (file:line)
- Reason for uncertainty
- Suggested confirmation method (who to ask, what to check)
```

---

## Phase 3: Knowledge Output

**Goal**: Transform analysis results into a maintainable document set.

### 3.1 Module Architecture Documentation

```
Prompt:
Based on the complete analysis of [module path], generate module architecture documentation.

Requirements:
1. Module responsibility (one sentence)
2. Public interface list
3. Internal component diagram (using Mermaid syntax)
4. Dependencies
5. Configuration items
6. Known issues and risks

Output to: docs/architecture/[module-name].md
```

**Output Template**:

```markdown
# [Module Name] Module Architecture

## Responsibility
[One-sentence description]

## Component Structure
​```mermaid
graph TD
    A[Controller] --> B[Service]
    B --> C[Repository]
    B --> D[External API]
    B --> E[Cache]
​```

## Public Interfaces
| Interface | Method | Description |
|-----------|--------|-------------|

## Dependencies
| Dependency | Type | Purpose |
|-----------|------|---------|

## Configuration
| Config Key | Default | Description |
|-----------|---------|-------------|

## Known Risks
| Risk | Severity | Description |
|------|----------|-------------|
```

### 3.2 Data Model Documentation

```
Prompt:
Analyze all data model definitions (Entity/Model/Schema/Migration) in the project and generate data model documentation.

Requirements:
1. Field list for each table (field name, type, constraints, meaning)
2. Inter-table relationships (one-to-many, many-to-many, foreign keys)
3. Index design
4. Enum field value domains and business meanings
5. Data lifecycle (when created, updated, deleted)

Output to: docs/architecture/data-model.md
```

### 3.3 API Documentation

```
Prompt:
Extract all API endpoints from route/Controller definitions and generate API documentation.

Requirements:
1. Grouped by module
2. For each endpoint: method, path, description, parameters, response, error codes
3. Authentication requirements
4. Whether paginated
5. Deprecated endpoints (if @Deprecated annotation or similar exists)

Output to: docs/api/[module-name].md
```

### 3.4 Risk Map

```
Prompt:
Based on the analysis of the entire project, generate a risk map.

Risk categories:
1. High-complexity code (functions with high cyclomatic complexity)
2. Core logic without tests
3. Hardcoded configuration values
4. Existing TODO/FIXME/HACK comments
5. Frequently changed hotspots (from git log analysis)
6. Dependencies on outdated or deprecated external libraries
7. Potential security issues (SQL concatenation, unvalidated input, etc.)

Output to: docs/architecture/risk-map.md
```

---

## Complete Example: Analyzing an Order Module

Below is a complete code archaeology example showing how to analyze a typical order module.

### Step 1: Global Scan of the Order Module

```
Prompt:
Analyze the src/modules/order/ directory and complete these tasks:

1. List all files and their approximate responsibilities
2. Find all entry points (API endpoints, event listeners, scheduled tasks)
3. List other modules and external services that the order module depends on
4. Find data model definitions
```

### Step 2: Deep-Dive into Order State Transitions

```
Prompt:
In the src/modules/order/ directory, find all code related to order status.

Focus analysis:
1. What statuses does an order have? Extract from enums/constants.
2. What are the transition rules between statuses?
3. What operations are triggered by each status change?
4. Is there logic for handling exceptional states?

Organize results into a state transition table.
```

### Step 3: Trace the Complete Data Flow for Order Creation

```
Prompt:
Trace the "create order" function from entry point to storage.

Starting from OrderController.create(), trace step by step:
1. What parameters does the Controller receive?
2. What processing does the Service layer do?
3. Which external services are called?
4. Which data tables are written to?
5. What response is returned?
6. What exception branches exist?

Annotate code location (filename:line) at each step.
```

### Step 4: Generate Order Module Documentation

```
Prompt:
Based on the above analysis of the order module, generate the following documents:

1. Module architecture doc → docs/architecture/order-module.md
2. Order API doc → docs/api/order-api.md
3. Order data model → The order section of docs/architecture/data-model.md
4. Order business rules → docs/architecture/order-business-rules.md

For each document, annotate using the confidence system:
- Confident content: [High Confidence]
- Inferred content: [Medium Confidence], with inference basis
- Uncertain content: [Low Confidence], with suggested confirmation method
```

---

## Usage Tips

### Work Cadence

| Phase | Scope | Recommended Approach |
|-------|-------|---------------------|
| Phase 1 Global Scan | Any scale | Complete in one pass, produce panorama |
| Phase 2 Module Deep Dive | Per module | Focus on 1 module per session, priority high to low |
| Phase 3 Knowledge Output | Alongside Phase 2 | Generate docs as each module analysis completes |

### Priority Ordering

Suggested priority for module analysis:

1. **Core business modules** (directly affecting revenue)
2. **High-frequency change modules** (from git log modification frequency)
3. **Cross-team dependency modules** (interfaces called by other teams)
4. **Problem-prone modules** (high bug rate areas)
5. **Modules scheduled for refactoring** (with planned technical improvements)

### Integration with knowledge-sync Skill

After code archaeology is complete, use the `knowledge-sync` Skill to establish a long-term sync mechanism, ensuring documentation stays consistent with code.

### Integration with generate-docs Skill

Code archaeology focuses on "reverse-understanding code," while generate-docs focuses on "generating documentation from code." Use them together:
- First use code archaeology to understand the code
- Then use generate-docs to generate standardized documentation
