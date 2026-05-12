# MCP Server Configuration Guide

> MCP (Model Context Protocol) Server configuration template for connecting AI agents to internal team tools.

---

## Overview

This document describes how to configure MCP servers to connect AI agents with the team's internal toolchain. MCP enables agents to directly query and operate tools like JIRA, GitLab, databases, and documentation systems.

## Configuration File

Copy the relevant sections from the template below into `.claude/settings.local.json` or `.claude/mcp-servers.json` based on your team's actual tooling.

### Project Management

#### JIRA

```json
{
  "jira": {
    "command": "npx",
    "args": ["-y", "@modelcontextprotocol/server-jira"],
    "env": {
      "JIRA_URL": "https://your-company.atlassian.net",
      "JIRA_USERNAME": "",
      "JIRA_API_TOKEN": ""
    }
  }
}
```

**Capabilities**: Query/update issues, link code changes to JIRA tickets.

### Code Hosting

#### GitLab

```json
{
  "gitlab": {
    "command": "npx",
    "args": ["-y", "@modelcontextprotocol/server-gitlab"],
    "env": {
      "GITLAB_URL": "https://gitlab.your-company.com",
      "GITLAB_TOKEN": ""
    }
  }
}
```

**Capabilities**: Manage MRs, trigger CI/CD pipelines, search code.

#### GitHub

```json
{
  "github": {
    "command": "npx",
    "args": ["-y", "@modelcontextprotocol/server-github"],
    "env": {
      "GITHUB_TOKEN": ""
    }
  }
}
```

**Capabilities**: Manage PRs, Issues, and Actions.

### Databases

#### PostgreSQL (Read-Only Recommended)

```json
{
  "postgres": {
    "command": "npx",
    "args": ["-y", "@modelcontextprotocol/server-postgres"],
    "env": {
      "POSTGRES_CONNECTION_STRING": "postgresql://readonly_user:password@localhost:5432/dbname"
    }
  }
}
```

**Capabilities**: Query database schema and run read-only queries to assist development.

#### MySQL (Read-Only Recommended)

```json
{
  "mysql": {
    "command": "npx",
    "args": ["-y", "@modelcontextprotocol/server-mysql"],
    "env": {
      "MYSQL_HOST": "localhost",
      "MYSQL_PORT": "3306",
      "MYSQL_USER": "readonly",
      "MYSQL_PASSWORD": "",
      "MYSQL_DATABASE": ""
    }
  }
}
```

**Capabilities**: Query database schema and run read-only queries.

### Documentation & Knowledge Base

#### Confluence

```json
{
  "confluence": {
    "command": "npx",
    "args": ["-y", "@modelcontextprotocol/server-confluence"],
    "env": {
      "CONFLUENCE_URL": "https://your-company.atlassian.net/wiki",
      "CONFLUENCE_USERNAME": "",
      "CONFLUENCE_API_TOKEN": ""
    }
  }
}
```

**Capabilities**: Search and retrieve team documentation.

### Notifications

#### Slack

```json
{
  "slack": {
    "command": "npx",
    "args": ["-y", "@modelcontextprotocol/server-slack"],
    "env": {
      "SLACK_BOT_TOKEN": ""
    }
  }
}
```

**Capabilities**: Send build/deployment notifications to channels.

### Custom MCP Server Example

```json
{
  "internal-docs": {
    "command": "node",
    "args": ["./scripts/mcp-server-internal-docs.js"],
    "env": {
      "DOCS_API_URL": "http://internal-docs-api.company.com",
      "DOCS_API_KEY": ""
    }
  }
}
```

**Capabilities**: Custom MCP server connecting to internal documentation systems.

---

## Security Guidelines

1. **Never hardcode API tokens** - Always inject via environment variables
2. **Use read-only accounts** for production database connections
3. **Limit agent database operations** to read-only queries
4. **Audit MCP server access logs** regularly
5. **Use `.env` files** for secret management and ensure `.env` is in `.gitignore`

## Recommended Integration Order

| Step | Tool | Rationale |
|------|------|-----------|
| 1 | GitLab/GitHub | Code operations provide the highest immediate value |
| 2 | JIRA | Project management integration |
| 3 | Database (read-only) | Assists development with schema queries |
| 4 | Documentation system | Knowledge retrieval |
| 5 | Monitoring/Notifications | Operations automation |

## Full Configuration Template

Combine the servers you need into a single configuration:

```json
{
  "mcpServers": {
    "gitlab": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-gitlab"],
      "env": {
        "GITLAB_URL": "",
        "GITLAB_TOKEN": ""
      }
    },
    "jira": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-jira"],
      "env": {
        "JIRA_URL": "",
        "JIRA_USERNAME": "",
        "JIRA_API_TOKEN": ""
      }
    },
    "postgres": {
      "command": "npx",
      "args": ["-y", "@modelcontextprotocol/server-postgres"],
      "env": {
        "POSTGRES_CONNECTION_STRING": ""
      }
    }
  }
}
```
