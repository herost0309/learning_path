# Skill: Release & Deploy

> Purpose: Standardized release and deployment workflow to reduce human error and deployment anxiety.

## Trigger

- Preparing to release a new version
- Deploying to staging/pre-production/production environments

## Input Requirements

- **Version number**: Follow Semantic Versioning (SemVer)
- **Change scope**: Main changes included in this release
- **Target environment**: Staging / Pre-production / Production
- **Rollback plan**: How to roll back if issues arise

## Execution Steps

### Step 1: Pre-Release Checks

```markdown
## Release Checklist

### Code Checks
- [ ] All MRs merged into the release branch
- [ ] Code freeze: no new MRs accepted
- [ ] No unresolved security vulnerabilities (dependency scan passed)

### Test Checks
- [ ] All unit tests pass
- [ ] All integration tests pass
- [ ] Regression tests cover core scenarios
- [ ] Performance tests (if performance-related changes are included)

### Configuration Checks
- [ ] Environment variables updated (if needed)
- [ ] Database migration scripts prepared (if needed)
- [ ] Feature flags configured correctly (if used)

### Documentation Checks
- [ ] CHANGELOG updated
- [ ] API documentation updated (if endpoint changes are included)
- [ ] Operations team notified (if needed)
```

### Step 2: Version Number Management

Determine the version number based on the type of change:

- **PATCH (x.y.Z)**: Bug fixes, no new features
- **MINOR (x.Y.z)**: New features, backward compatible
- **MAJOR (X.y.z)**: Breaking changes

### Step 3: Build & Package

```bash
# Adjust according to project setup
# 1. Pull latest code
# 2. Install dependencies
# 3. Run tests
# 4. Build
# 5. Package Docker image (if applicable)
# 6. Push to artifact repository
```

### Step 4: Deployment Pipeline

```
Staging -> Pre-production -> Production
   |            |               |
Auto-deploy  Auto-deploy  Deploy after manual confirmation
```

After deploying to each environment:
1. Smoke tests pass
2. Core functionality verified
3. Log monitoring shows no anomalies

### Step 5: Post-Release Verification

- [ ] Monitoring dashboard shows no anomaly alerts
- [ ] Core business flows verified
- [ ] Logs show no Error-level exceptions (excluding expected ones)
- [ ] Performance metrics within normal range
- [ ] Observe for 15-30 minutes to confirm stability

### Step 6: Wrap-Up

- Update CHANGELOG
- Notify relevant teams
- Archive the release branch (if used)
- Document lessons learned from the release

## Rollback Plan

If issues are discovered after release:

1. **L1 - Config Rollback**: Disable the feature flag (fastest, seconds)
2. **L2 - Version Rollback**: Revert to the previous version image (minutes)
3. **L3 - Data Rollback**: Execute data rollback scripts (requires careful assessment)

## Notes

- Production releases are recommended on working days; avoid Friday deployments
- Major changes should use a canary/grayscale deployment strategy
- Keep the rollback plan ready at all times
