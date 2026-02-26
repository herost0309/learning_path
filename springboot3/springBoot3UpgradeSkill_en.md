# Spring Boot 3.x Upgrade Skill Design Document

---

## 1. Skill Design Overview

### 1.1 Skill Metadata

| Property | Value |
|----------|-------|
| **Skill Name** | `upgrade-spring-boot3` |
| **Version** | 1.0.0 |
| **Description** | Automated upgrade from Spring Boot 2.x (JDK 8) to Spring Boot 3.x (JDK 21) |
| **Trigger** | Manual invocation `/upgrade-spring-boot3` |
| **Prerequisites** | Spring Boot 2.x project, Git repository |
| **Estimated Duration** | 30-60 minutes (depending on project size) |

### 1.2 Skill Objectives

```
Input:  Spring Boot 2.x + JDK 8 project
Output: Spring Boot 3.x + JDK 21 project (compilable, runnable)
```

### 1.3 Core Capabilities

| Capability | Description |
|------------|-------------|
| **Project Analysis** | Automatically identify project structure, dependencies, build tools |
| **Risk Assessment** | Generate upgrade risk report with severity levels |
| **Automated Migration** | Use OpenRewrite for automatic code transformation |
| **Manual Fix Guidance** | Provide list of issues that cannot be auto-fixed |
| **Validation Testing** | Execute compilation and test verification |

---

## 2. Skill Architecture Design

### 2.1 Overall Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                      upgrade-spring-boot3 Skill                         │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│   ┌─────────────┐      ┌─────────────┐      ┌─────────────┐           │
│   │   Analyzer  │─────▶│  Migrator   │─────▶│  Validator  │           │
│   └─────────────┘      └─────────────┘      └─────────────┘           │
│          │                    │                    │                    │
│          ▼                    ▼                    ▼                    │
│   ┌─────────────┐      ┌─────────────┐      ┌─────────────┐           │
│   │Project Scan │      │Code Transform│     │Compile Check│           │
│   │Depend. Scan │      │Depend. Upgrade│    │Test Execution│          │
│   │Risk Assess. │      │Config Update │     │Report Gen.  │           │
│   └─────────────┘      └─────────────┘      └─────────────┘           │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

### 2.2 Execution Flow

```
START
  │
  ▼
┌──────────────────────┐
│ 1. Environment Check │ ──▶ JDK 21? Maven 3.9+? Git?
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│ 2. Project Analysis  │ ──▶ Detect build tool, dependencies, code structure
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│ 3. Risk Assessment   │ ──▶ Generate risk report, user confirmation
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│ 4. Create Branch     │ ──▶ git checkout -b upgrade/spring-boot-3
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│ 5. JDK Upgrade       │ ──▶ Update pom.xml/build.gradle
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│ 6. Dependency Update │ ──▶ Spring Boot 3.x, Spring Cloud 2023.x
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│ 7. Code Migration    │ ──▶ javax -> jakarta, Security config
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│ 8. Compile Verify    │ ──▶ mvn compile / gradle compileJava
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│ 9. Test Verify       │ ──▶ mvn test / gradle test
└──────────┬───────────┘
           │
           ▼
┌──────────────────────┐
│ 10. Generate Report  │ ──▶ Migration report, pending items
└──────────┬───────────┘
           │
           ▼
END
```

### 2.3 Module Design

#### 2.3.1 Analyzer Module

```yaml
module_name: analyzer
responsibility: Project analysis and risk assessment
input: Project root directory
output: Analysis report (JSON)

subtasks:
  - detectBuildTool: Detect build tool (Maven/Gradle)
  - scanDependencies: Scan all dependencies and versions
  - countJavaxImports: Count javax.* import statements
  - findSecurityConfigs: Find security configuration classes
  - findJpaEntities: Find JPA entity classes
  - findElasticsearchUsage: Find Elasticsearch client usage
  - findNativeLibraries: Find native JNI library usage
  - assessRisks: Assess upgrade risks
  - generateReport: Generate analysis report
```

#### 2.3.2 Migrator Module

```yaml
module_name: migrator
responsibility: Execute code and configuration migration
input: Analysis report
output: Migrated code

subtasks:
  - upgradeJavaVersion: Upgrade Java version to 21
  - upgradeSpringBoot: Upgrade Spring Boot to 3.x
  - migrateJavaxToJakarta: javax -> jakarta namespace migration
  - upgradeSecurityConfig: Upgrade Spring Security configuration
  - upgradeDependencies: Upgrade third-party dependencies
  - updateConfiguration: Update application.yml/properties
  - updateDockerfile: Update Docker base image
```

#### 2.3.3 Validator Module

```yaml
module_name: validator
responsibility: Validate migration results
input: Migrated project
output: Validation report

subtasks:
  - compileProject: Compile project
  - runTests: Run tests
  - checkApplicationStart: Check if application can start
  - verifyEndpoints: Verify REST endpoints
  - generateReport: Generate final report
```

---

## 3. Skill Detailed Definition

### 3.1 Skill Configuration File

```yaml
# skill.yaml
name: upgrade-spring-boot3
version: 1.0.0
description: Upgrade Spring Boot 2.x (JDK 8) to Spring Boot 3.x (JDK 21)
author: Claude

# Trigger Configuration
triggers:
  - command: "/upgrade-spring-boot3"
  - pattern: "upgrade.*spring.*boot.*3"
  - pattern: "spring.*boot.*migration"
  - pattern: "jdk.*21.*upgrade"
  - pattern: "migrate.*spring.*boot"

# Prerequisites
prerequisites:
  - name: "JDK 21 installed"
    check: "java --version | grep 21"
    fix: "Please install JDK 21 and set JAVA_HOME"
  - name: "Maven 3.9+ or Gradle 8+"
    check: "mvn --version || gradle --version"
    fix: "Please upgrade build tool to Maven 3.9+ or Gradle 8+"
  - name: "Git repository"
    check: "git rev-parse --git-dir"
    fix: "Please initialize git repository"
  - name: "Clean working directory"
    check: "git status --porcelain"
    fix: "Please commit or stash uncommitted changes"

# Parameter Configuration
parameters:
  - name: targetSpringBootVersion
    type: string
    default: "3.2.0"
    description: "Target Spring Boot version"
  - name: targetJavaVersion
    type: integer
    default: 21
    description: "Target Java version"
  - name: useOpenRewrite
    type: boolean
    default: true
    description: "Use OpenRewrite for automatic migration"
  - name: skipTests
    type: boolean
    default: false
    description: "Skip test execution"
  - name: dryRun
    type: boolean
    default: false
    description: "Analyze only without making changes"
  - name: includeElasticsearchMigration
    type: boolean
    default: true
    description: "Include Elasticsearch client migration"

# Execution Steps
steps:
  - id: analyze
    name: "Project Analysis"
    description: "Analyze project structure, dependencies, and risks"

  - id: backup
    name: "Create Backup"
    description: "Create git branch for migration"
    depends_on: []

  - id: upgrade-jdk
    name: "Upgrade JDK"
    description: "Update Java version to 21"
    depends_on: [backup]

  - id: upgrade-dependencies
    name: "Upgrade Dependencies"
    description: "Upgrade Spring Boot and related dependencies"
    depends_on: [upgrade-jdk]

  - id: migrate-code
    name: "Migrate Code"
    description: "Migrate javax to jakarta and update configurations"
    depends_on: [upgrade-dependencies]

  - id: migrate-security
    name: "Migrate Security"
    description: "Update Spring Security configurations"
    depends_on: [migrate-code]

  - id: validate
    name: "Validate"
    description: "Compile and test the project"
    depends_on: [migrate-security]

  - id: report
    name: "Generate Report"
    description: "Generate migration report and pending items"
    depends_on: [validate]

# Output Configuration
outputs:
  - name: migrationReport
    type: file
    path: "migration-report.md"
  - name: pendingItems
    type: file
    path: "pending-manual-fixes.md"
  - name: analysisReport
    type: file
    path: "analysis-report.json"

# Rollback Configuration
rollback:
  enabled: true
  command: "git checkout -"
```

### 3.2 Execution Scripts

#### Step 1: Project Analysis Script

```bash
#!/bin/bash
# analyze.sh - Spring Boot 3.x Migration Analysis

set -e

echo "=========================================="
echo "  Spring Boot 3.x Migration Analysis"
echo "=========================================="
echo ""

# Initialize report file
REPORT_FILE="analysis-report.json"
echo "{" > $REPORT_FILE

# Detect build tool
echo ">>> Detecting Build Tool..."
if [ -f "pom.xml" ]; then
    BUILD_TOOL="maven"
    echo "Build Tool: Maven"

    # Extract current Spring Boot version
    CURRENT_SPRING_BOOT=$(xmllint --xpath "//*[local-name()='parent']/*[local-name()='version']/text()" pom.xml 2>/dev/null | head -1)
    if [ -z "$CURRENT_SPRING_BOOT" ]; then
        CURRENT_SPRING_BOOT=$(grep -oP '(?<=<spring-boot.version>)[^<]+' pom.xml 2>/dev/null)
    fi
    echo "Current Spring Boot Version: ${CURRENT_SPRING_BOOT:-Unknown}"

    # Extract Java version
    CURRENT_JAVA=$(grep -oP '(?<=<java.version>)[^<]+' pom.xml 2>/dev/null || echo "Unknown")
    echo "Current Java Version: ${CURRENT_JAVA}"

elif [ -f "build.gradle" ] || [ -f "build.gradle.kts" ]; then
    BUILD_TOOL="gradle"
    echo "Build Tool: Gradle"
    CURRENT_JAVA=$(grep "sourceCompatibility" build.gradle 2>/dev/null | grep -oP "'\K[^']+" || echo "Unknown")
    echo "Current Java Version: ${CURRENT_JAVA}"
else
    echo "ERROR: No build file found (pom.xml or build.gradle)"
    exit 1
fi

echo ""
echo "  \"buildTool\": \"$BUILD_TOOL\"," >> $REPORT_FILE
echo "  \"currentSpringBoot\": \"$CURRENT_SPRING_BOOT\"," >> $REPORT_FILE
echo "  \"currentJava\": \"$CURRENT_JAVA\"," >> $REPORT_FILE

# Count javax imports
echo ">>> Analyzing javax.* Imports..."
echo ""
JAVAX_SERVLET=$(find . -name "*.java" -exec grep -l "import javax\.servlet" {} \; 2>/dev/null | grep -v target | grep -v build | wc -l)
JAVAX_PERSISTENCE=$(find . -name "*.java" -exec grep -l "import javax\.persistence" {} \; 2>/dev/null | grep -v target | grep -v build | wc -l)
JAVAX_VALIDATION=$(find . -name "*.java" -exec grep -l "import javax\.validation" {} \; 2>/dev/null | grep -v target | grep -v build | wc -l)
JAVAX_ANNOTATION=$(find . -name "*.java" -exec grep -l "import javax\.annotation" {} \; 2>/dev/null | grep -v target | grep -v build | wc -l)
JAVAX_TRANSACTION=$(find . -name "*.java" -exec grep -l "import javax\.transaction" {} \; 2>/dev/null | grep -v target | grep -v build | wc -l)
JAVAX_MAIL=$(find . -name "*.java" -exec grep -l "import javax\.mail" {} \; 2>/dev/null | grep -v target | grep -v build | wc -l)
JAVAX_WSRS=$(find . -name "*.java" -exec grep -l "import javax\.ws\.rs" {} \; 2>/dev/null | grep -v target | grep -v build | wc -l)

TOTAL_JAVAX=$((JAVAX_SERVLET + JAVAX_PERSISTENCE + JAVAX_VALIDATION + JAVAX_ANNOTATION + JAVAX_TRANSACTION + JAVAX_MAIL + JAVAX_WSRS))

echo "javax.* Import Analysis:"
echo "  - javax.servlet:     $JAVAX_SERVLET files"
echo "  - javax.persistence: $JAVAX_PERSISTENCE files"
echo "  - javax.validation:  $JAVAX_VALIDATION files"
echo "  - javax.annotation:  $JAVAX_ANNOTATION files"
echo "  - javax.transaction: $JAVAX_TRANSACTION files"
echo "  - javax.mail:        $JAVAX_MAIL files"
echo "  - javax.ws.rs:       $JAVAX_WSRS files"
echo "  ----------------------------"
echo "  Total:               $TOTAL_JAVAX files"

echo "  \"javaxImports\": {" >> $REPORT_FILE
echo "    \"servlet\": $JAVAX_SERVLET," >> $REPORT_FILE
echo "    \"persistence\": $JAVAX_PERSISTENCE," >> $REPORT_FILE
echo "    \"validation\": $JAVAX_VALIDATION," >> $REPORT_FILE
echo "    \"annotation\": $JAVAX_ANNOTATION," >> $REPORT_FILE
echo "    \"transaction\": $JAVAX_TRANSACTION," >> $REPORT_FILE
echo "    \"mail\": $JAVAX_MAIL," >> $REPORT_FILE
echo "    \"wsrs\": $JAVAX_WSRS," >> $REPORT_FILE
echo "    \"total\": $TOTAL_JAVAX" >> $REPORT_FILE
echo "  }," >> $REPORT_FILE

# Find Spring Security configurations
echo ""
echo ">>> Analyzing Spring Security Configurations..."
SECURITY_CONFIGS=$(find . -name "*.java" -exec grep -l "WebSecurityConfigurerAdapter" {} \; 2>/dev/null | grep -v target | grep -v build)
SECURITY_COUNT=$(echo "$SECURITY_CONFIGS" | grep -c "java" 2>/dev/null || echo "0")

if [ -n "$SECURITY_CONFIGS" ]; then
    echo "Files using WebSecurityConfigurerAdapter (needs update):"
    echo "$SECURITY_CONFIGS"
else
    echo "No WebSecurityConfigurerAdapter usage found"
fi

echo "  \"securityConfigs\": $SECURITY_COUNT," >> $REPORT_FILE

# Find Elasticsearch usage
echo ""
echo ">>> Analyzing Elasticsearch Usage..."
ES_HIGH_LEVEL=$(find . -name "*.java" -exec grep -l "RestHighLevelClient" {} \; 2>/dev/null | grep -v target | grep -v build | wc -l)
ES_JAVA_CLIENT=$(find . -name "*.java" -exec grep -l "ElasticsearchClient" {} \; 2>/dev/null | grep -v target | grep -v build | wc -l)

echo "Elasticsearch Analysis:"
echo "  - RestHighLevelClient usage: $ES_HIGH_LEVEL files (NEEDS MIGRATION)"
echo "  - ElasticsearchClient usage: $ES_JAVA_CLIENT files (already migrated)"

echo "  \"elasticsearch\": {" >> $REPORT_FILE
echo "    \"highLevelClient\": $ES_HIGH_LEVEL," >> $REPORT_FILE
echo "    \"javaClient\": $ES_JAVA_CLIENT" >> $REPORT_FILE
echo "  }," >> $REPORT_FILE

# Find native library usage
echo ""
echo ">>> Checking for Native Libraries..."
NATIVE_LIBS=$(find . -name "*.java" -exec grep -l "native " {} \; 2>/dev/null | grep -v target | grep -v build)
NATIVE_COUNT=$(echo "$NATIVE_LIBS" | grep -c "java" 2>/dev/null || echo "0")

if [ -n "$NATIVE_LIBS" ]; then
    echo "WARNING: Native methods found in:"
    echo "$NATIVE_LIBS"
    echo "These may need to be rebuilt for JDK 21"
else
    echo "No native methods found"
fi

echo "  \"nativeLibraries\": $NATIVE_COUNT," >> $REPORT_FILE

# Risk Assessment
echo ""
echo "=========================================="
echo "  Risk Assessment"
echo "=========================================="

RISK_LEVEL="LOW"
RISK_FACTORS=""

if [ "$TOTAL_JAVAX" -gt 100 ]; then
    RISK_LEVEL="HIGH"
    RISK_FACTORS="$RISK_FACTORS- Large number of javax imports ($TOTAL_JAVAX)\n"
elif [ "$TOTAL_JAVAX" -gt 50 ]; then
    RISK_LEVEL="MEDIUM"
    RISK_FACTORS="$RISK_FACTORS- Moderate javax imports ($TOTAL_JAVAX)\n"
fi

if [ "$SECURITY_COUNT" -gt 0 ]; then
    if [ "$RISK_LEVEL" = "LOW" ]; then
        RISK_LEVEL="MEDIUM"
    fi
    RISK_FACTORS="$RISK_FACTORS- Spring Security configuration needs rewrite\n"
fi

if [ "$ES_HIGH_LEVEL" -gt 0 ]; then
    RISK_LEVEL="HIGH"
    RISK_FACTORS="$RISK_FACTORS- Elasticsearch client migration required\n"
fi

if [ "$NATIVE_COUNT" -gt 0 ]; then
    RISK_LEVEL="HIGH"
    RISK_FACTORS="$RISK_FACTORS- Native libraries need rebuild\n"
fi

echo ""
echo "Overall Risk Level: $RISK_LEVEL"
echo ""
echo "Risk Factors:"
echo -e "$RISK_FACTORS"

echo "  \"riskAssessment\": {" >> $REPORT_FILE
echo "    \"level\": \"$RISK_LEVEL\"," >> $REPORT_FILE
echo "    \"factors\": \"$RISK_FACTORS\"" >> $REPORT_FILE
echo "  }" >> $REPORT_FILE
echo "}" >> $REPORT_FILE

echo ""
echo "=========================================="
echo "  Analysis Complete"
echo "=========================================="
echo "Report saved to: $REPORT_FILE"
```

#### Step 2: Migration Script

```bash
#!/bin/bash
# migrate.sh - Execute Spring Boot 3.x Migration

set -e

SPRING_BOOT_VERSION=${1:-"3.2.0"}
JAVA_VERSION=${2:-21}

echo "=========================================="
echo "  Spring Boot 3.x Migration"
echo "=========================================="
echo ""
echo "Target Spring Boot: $SPRING_BOOT_VERSION"
echo "Target Java: $JAVA_VERSION"
echo ""

# Create migration branch
BRANCH_NAME="upgrade/spring-boot-3-$(date +%Y%m%d-%H%M%S)"
echo ">>> Creating migration branch: $BRANCH_NAME"
git checkout -b "$BRANCH_NAME"

# Update pom.xml (Maven)
if [ -f "pom.xml" ]; then
    echo ""
    echo ">>> Updating pom.xml..."

    # Backup original
    cp pom.xml pom.xml.bak

    # Update Spring Boot version in parent
    if grep -q "spring-boot-starter-parent" pom.xml; then
        sed -i "s|<version>2\.[0-9]\+\.[0-9]\+</version>|<version>${SPRING_BOOT_VERSION}</version>|" pom.xml
    fi

    # Update Java version
    sed -i "s|<java.version>[0-9]\+</java.version>|<java.version>${JAVA_VERSION}</java.version>|" pom.xml
    sed -i "s|<maven.compiler.source>[0-9]\+</maven.compiler.source>|<maven.compiler.source>${JAVA_VERSION}</maven.compiler.source>|" pom.xml
    sed -i "s|<maven.compiler.target>[0-9]\+</maven.compiler.target>|<maven.compiler.target>${JAVA_VERSION}</maven.compiler.target>|" pom.xml

    # Update Spring Cloud version if present
    if grep -q "spring-cloud.version" pom.xml; then
        sed -i "s|<spring-cloud.version>[^<]*</spring-cloud.version>|<spring-cloud.version>2023.0.0</spring-cloud.version>|" pom.xml
    fi

    echo "pom.xml updated"
fi

# Update build.gradle (Gradle)
if [ -f "build.gradle" ]; then
    echo ""
    echo ">>> Updating build.gradle..."

    cp build.gradle build.gradle.bak

    sed -i "s/sourceCompatibility = '[0-9]\+'/sourceCompatibility = '${JAVA_VERSION}'/" build.gradle
    sed -i "s/targetCompatibility = '[0-9]\+'/targetCompatibility = '${JAVA_VERSION}'/" build.gradle
    sed -i "s|id 'org.springframework.boot' version '[^']*'|id 'org.springframework.boot' version '${SPRING_BOOT_VERSION}'|" build.gradle

    echo "build.gradle updated"
fi

# Update build.gradle.kts (Kotlin DSL)
if [ -f "build.gradle.kts" ]; then
    echo ""
    echo ">>> Updating build.gradle.kts..."

    cp build.gradle.kts build.gradle.kts.bak

    sed -i "s/languageVersion.set(JavaLanguageVersion.of([0-9]\+))/languageVersion.set(JavaLanguageVersion.of(${JAVA_VERSION}))/" build.gradle.kts

    echo "build.gradle.kts updated"
fi

# Migrate javax to jakarta in Java files
echo ""
echo ">>> Migrating javax.* to jakarta.* in Java files..."

find . -type f -name "*.java" ! -path "*/target/*" ! -path "*/build/*" -exec sed -i \
    -e 's/import javax\.servlet\./import jakarta.servlet./g' \
    -e 's/import javax\.persistence\./import jakarta.persistence./g' \
    -e 's/import javax\.validation\./import jakarta.validation./g' \
    -e 's/import javax\.annotation\./import jakarta.annotation./g' \
    -e 's/import javax\.transaction\./import jakarta.transaction./g' \
    -e 's/import javax\.mail\./import jakarta.mail./g' \
    -e 's/import javax\.ws\.rs\./import jakarta.ws.rs./g' \
    -e 's/import javax\.jms\./import jakarta.jms./g' \
    -e 's/import javax\.json\./import jakarta.json./g' \
    -e 's/@javax\.persistence\./@jakarta.persistence./g' \
    -e 's/@javax\.validation\./@jakarta.validation./g' \
    -e 's/@javax\.annotation\./@jakarta.annotation./g' \
    {} \;

echo "Java files migrated"

# Update configuration files
echo ""
echo ">>> Updating configuration files..."

# XML files
find . -type f -name "*.xml" ! -path "*/target/*" ! -path "*/build/*" -exec sed -i \
    -e 's/javax\./jakarta./g' \
    {} \;

# YAML files (be careful with format)
find . -type f \( -name "*.yml" -o -name "*.yaml" \) ! -path "*/target/*" ! -path "*/build/*" -exec sed -i \
    -e 's/javax\./jakarta./g' \
    {} \;

# Properties files
find . -type f -name "*.properties" ! -path "*/target/*" ! -path "*/build/*" -exec sed -i \
    -e 's/javax\./jakarta./g' \
    {} \;

echo "Configuration files updated"

# Update Dockerfile if exists
if [ -f "Dockerfile" ]; then
    echo ""
    echo ">>> Updating Dockerfile..."

    cp Dockerfile Dockerfile.bak

    # Update Java 8 base images to Java 21
    sed -i 's|openjdk:8|eclipse-temurin:21|g' Dockerfile
    sed -i 's|openjdk:11|eclipse-temurin:21|g' Dockerfile
    sed -i 's|openjdk:17|eclipse-temurin:21|g' Dockerfile
    sed -i 's|java:8|eclipse-temurin:21|g' Dockerfile
    sed -i 's|adoptopenjdk/openjdk8|eclipse-temurin:21|g' Dockerfile
    sed -i 's|adoptopenjdk/openjdk11|eclipse-temurin:21|g' Dockerfile

    echo "Dockerfile updated"
fi

echo ""
echo "=========================================="
echo "  Migration Complete"
echo "=========================================="
echo ""
echo "Branch: $BRANCH_NAME"
echo ""
echo "Next steps:"
echo "  1. Review changes: git diff"
echo "  2. Run validation: ./validate.sh"
echo "  3. Fix any remaining issues manually"
echo "  4. Test thoroughly before merging"
```

#### Step 3: Validation Script

```bash
#!/bin/bash
# validate.sh - Validate Spring Boot 3.x Migration

echo "=========================================="
echo "  Migration Validation"
echo "=========================================="

VALIDATION_PASSED=true

# Check for remaining javax imports
echo ""
echo ">>> Checking for remaining javax.* imports..."

REMAINING_JAVAX=$(find . -type f -name "*.java" ! -path "*/target/*" ! -path "*/build/*" -exec grep -l "import javax\." {} \; 2>/dev/null | wc -l)

if [ "$REMAINING_JAVAX" -gt 0 ]; then
    echo "WARNING: $REMAINING_JAVAX files still contain javax.* imports:"
    find . -type f -name "*.java" ! -path "*/target/*" ! -path "*/build/*" -exec grep -l "import javax\." {} \; 2>/dev/null
    VALIDATION_PASSED=false
else
    echo "OK: No javax.* imports found"
fi

# Check for WebSecurityConfigurerAdapter usage
echo ""
echo ">>> Checking for deprecated WebSecurityConfigurerAdapter..."

REMAINING_ADAPTER=$(find . -type f -name "*.java" ! -path "*/target/*" ! -path "*/build/*" -exec grep -l "WebSecurityConfigurerAdapter" {} \; 2>/dev/null | wc -l)

if [ "$REMAINING_ADAPTER" -gt 0 ]; then
    echo "WARNING: $REMAINING_ADAPTER files still use WebSecurityConfigurerAdapter:"
    find . -type f -name "*.java" ! -path "*/target/*" ! -path "*/build/*" -exec grep -l "WebSecurityConfigurerAdapter" {} \; 2>/dev/null
    echo "These need to be migrated to SecurityFilterChain bean pattern"
    VALIDATION_PASSED=false
else
    echo "OK: No WebSecurityConfigurerAdapter usage found"
fi

# Check for RestHighLevelClient usage
echo ""
echo ">>> Checking for deprecated Elasticsearch client..."

REMAINING_ES=$(find . -type f -name "*.java" ! -path "*/target/*" ! -path "*/build/*" -exec grep -l "RestHighLevelClient" {} \; 2>/dev/null | wc -l)

if [ "$REMAINING_ES" -gt 0 ]; then
    echo "WARNING: $REMAINING_ES files still use RestHighLevelClient:"
    find . -type f -name "*.java" ! -path "*/target/*" ! -path "*/build/*" -exec grep -l "RestHighLevelClient" {} \; 2>/dev/null
    echo "These need to be migrated to ElasticsearchClient"
    VALIDATION_PASSED=false
else
    echo "OK: No deprecated Elasticsearch client usage found"
fi

# Compile project
echo ""
echo ">>> Compiling project..."

if [ -f "pom.xml" ]; then
    mvn clean compile -DskipTests 2>&1 | tail -20
    COMPILE_RESULT=${PIPESTATUS[0]}
elif [ -f "build.gradle" ]; then
    ./gradlew clean compileJava 2>&1 | tail -20
    COMPILE_RESULT=${PIPESTATUS[0]}
fi

if [ "$COMPILE_RESULT" -eq 0 ]; then
    echo "OK: Compilation successful"
else
    echo "ERROR: Compilation failed"
    VALIDATION_PASSED=false
fi

# Run tests
echo ""
echo ">>> Running tests..."

if [ -f "pom.xml" ]; then
    mvn test 2>&1 | tail -20
    TEST_RESULT=${PIPESTATUS[0]}
elif [ -f "build.gradle" ]; then
    ./gradlew test 2>&1 | tail -20
    TEST_RESULT=${PIPESTATUS[0]}
fi

if [ "$TEST_RESULT" -eq 0 ]; then
    echo "OK: All tests passed"
else
    echo "WARNING: Some tests failed"
    VALIDATION_PASSED=false
fi

# Summary
echo ""
echo "=========================================="
echo "  Validation Summary"
echo "=========================================="

if [ "$VALIDATION_PASSED" = true ]; then
    echo "Status: PASSED"
    echo ""
    echo "The migration appears to be successful."
    echo "Please perform manual testing before deploying to production."
else
    echo "Status: NEEDS ATTENTION"
    echo ""
    echo "Some issues require manual intervention."
    echo "Please review the warnings above and fix accordingly."
fi

echo ""
echo "Generated files:"
echo "  - migration-report.md"
echo "  - pending-manual-fixes.md"
```

---

## 4. Skill Prompt Templates

### 4.1 System Prompt

```markdown
# Spring Boot 3.x Upgrade Skill

You are an expert in Spring Boot migration. Your task is to help users upgrade their
Spring Boot 2.x applications (running on JDK 8) to Spring Boot 3.x (running on JDK 21).

## Key Migration Tasks

1. **JDK Upgrade**: Update Java version from 8 to 21
2. **Spring Boot Upgrade**: Update from 2.x to 3.2.x
3. **Namespace Migration**: Replace all `javax.*` imports with `jakarta.*`
4. **Security Configuration**: Update Spring Security to use lambda DSL
5. **Dependency Updates**: Upgrade all related dependencies
6. **Configuration Updates**: Update application.yml/properties
7. **Elasticsearch Migration**: Migrate from RestHighLevelClient to Java API Client

## Migration Rules

### Namespace Mappings
- `javax.servlet.*` → `jakarta.servlet.*`
- `javax.persistence.*` → `jakarta.persistence.*`
- `javax.validation.*` → `jakarta.validation.*`
- `javax.annotation.*` → `jakarta.annotation.*`
- `javax.transaction.*` → `jakarta.transaction.*`
- `javax.mail.*` → `jakarta.mail.*`
- `javax.jms.*` → `jakarta.jms.*`
- `javax.ws.rs.*` → `jakarta.ws.rs.*`

### Security Configuration Pattern

**Before (Spring Security 5.x):**
```java
@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.authorizeRequests()
            .antMatchers("/public/**").permitAll();
    }
}
```

**After (Spring Security 6.x):**
```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
            .requestMatchers("/public/**").permitAll()
        );
        return http.build();
    }
}
```

### Elasticsearch Migration Pattern

**Before (RestHighLevelClient):**
```java
SearchRequest request = new SearchRequest("index");
SearchSourceBuilder builder = new SearchSourceBuilder();
builder.query(QueryBuilders.matchQuery("field", "value"));
request.source(builder);
SearchResponse response = client.search(request, RequestOptions.DEFAULT);
```

**After (Java API Client):**
```java
SearchResponse<Document> response = client.search(s -> s
    .index("index")
    .query(q -> q
        .match(m -> m.field("field").query("value"))
    ),
    Document.class
);
```

## Execution Process

1. Analyze the project structure and dependencies
2. Identify all files that need modification
3. Assess risks and get user confirmation
4. Create a backup branch
5. Apply migrations systematically
6. Validate compilation and tests
7. Generate migration report

## Risk Assessment Criteria

- **CRITICAL**: Elasticsearch client usage, Native JNI libraries
- **HIGH**: Large number of javax imports (>100), Custom security configs
- **MEDIUM**: Moderate javax imports (50-100), Spring Batch usage
- **LOW**: Minimal javax imports (<50), Standard configurations

Always verify changes and ask for user confirmation before proceeding with risky operations.
```

### 4.2 User Interaction Template

```markdown
## Upgrade Confirmation

I have analyzed your project. Here is the upgrade summary:

### Project Information
- **Build Tool**: {buildTool}
- **Current Spring Boot**: {currentVersion}
- **Target Spring Boot**: {targetVersion}
- **Current JDK**: {currentJdk}
- **Target JDK**: {targetJdk}

### Files Requiring Modification
- Java source files: {javaFileCount}
- Files with javax.* imports: {javaxFileCount}
- Configuration files: {configFileCount}
- Security config files: {securityConfigCount}

### Risk Assessment
- **Risk Level**: {riskLevel}
- **Key Risk Factors**:
{riskItems}

### Upgrade Steps
1. Create migration branch `upgrade/spring-boot-3`
2. Update build configuration (pom.xml/build.gradle)
3. Migrate javax → jakarta namespace
4. Update Spring Security configuration
5. Upgrade third-party dependencies
6. Update Docker configuration (if applicable)
7. Compile verification
8. Run tests

### Estimated Impact
- Files to be modified: ~{modifiedFileCount}
- Manual fixes required: ~{manualFixCount}

Do you want to proceed with the upgrade? [Y/n]
```

---

## 5. Output Report Templates

### 5.1 Migration Report Template

```markdown
# Spring Boot 3.x Migration Report

## Executive Summary

| Item | Details |
|------|---------|
| **Execution Time** | {timestamp} |
| **Project Name** | {projectName} |
| **Original Version** | Spring Boot {oldVersion} + JDK {oldJdk} |
| **New Version** | Spring Boot {newVersion} + JDK {newJdk} |
| **Status** | {status} |
| **Branch** | {branchName} |

## Change Statistics

| Type | Count |
|------|-------|
| Modified Java files | {modifiedJavaFiles} |
| Modified configuration files | {modifiedConfigFiles} |
| javax → jakarta migrations | {namespaceMigrations} |
| Dependency updates | {dependencyUpdates} |

## Detailed Changes

### 1. Dependency Updates

| Dependency | Old Version | New Version |
|------------|-------------|-------------|
| spring-boot-starter-parent | 2.7.x | 3.2.0 |
| spring-boot-starter-web | 2.7.x | 3.2.0 |
| spring-boot-starter-security | 2.7.x | 3.2.0 |
| spring-boot-starter-data-jpa | 2.7.x | 3.2.0 |
| hibernate-core | 5.6.x | 6.4.x |
| lombok | x.x.x | 1.18.30 |

### 2. Code Changes

#### Namespace Migration
- `javax.servlet` → `jakarta.servlet`: {count} occurrences
- `javax.persistence` → `jakarta.persistence`: {count} occurrences
- `javax.validation` → `jakarta.validation`: {count} occurrences
- `javax.annotation` → `jakarta.annotation`: {count} occurrences
- `javax.transaction` → `jakarta.transaction`: {count} occurrences

#### Security Configuration Updates
- Files modified: {securityFileCount}
- Migration type: WebSecurityConfigurerAdapter → SecurityFilterChain

### 3. Configuration Changes

| File | Changes |
|------|---------|
| application.yml | Updated Hibernate dialect, added Jakarta namespace refs |
| pom.xml | Updated versions and Java properties |
| Dockerfile | Updated base image to eclipse-temurin:21 |

## Validation Results

| Check | Status |
|-------|--------|
| Compilation | {compileStatus} |
| Unit Tests | {testStatus} |
| Integration Tests | {integrationTestStatus} |
| Remaining javax imports | {remainingJavax} |

## Pending Items

The following items require manual handling:

1. [ ] {pendingItem1}
2. [ ] {pendingItem2}
3. [ ] {pendingItem3}

## Rollback Instructions

To rollback, execute:

```bash
git checkout {originalBranch}
git branch -D {migrationBranch}
```

## Next Steps

1. Review and resolve pending items
2. Run full test suite
3. Test in development environment
4. Perform performance testing
5. Deploy to staging environment
6. Conduct user acceptance testing
7. Plan production deployment

---
*Report generated by upgrade-spring-boot3 skill on {timestamp}*
```

### 5.2 Pending Manual Fixes Template

```markdown
# Pending Manual Fixes

The following issues could not be automatically fixed and require manual intervention:

---

## 1. Custom Security Configuration

**File**: `src/main/java/com/example/config/CustomSecurityConfig.java`

**Issue**: Uses `WebSecurityConfigurerAdapter` with custom authentication logic

**Suggested Fix**:
```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class CustomSecurityConfig {

    private final UserDetailsService userDetailsService;

    public CustomSecurityConfig(UserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        // Migrate your original configure() logic here
        http.authorizeHttpRequests(auth -> auth
            // Add your authorization rules
        );
        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(HttpSecurity http) throws Exception {
        AuthenticationManagerBuilder auth = http.getSharedObject(AuthenticationManagerBuilder.class);
        auth.userDetailsService(userDetailsService).passwordEncoder(passwordEncoder());
        return auth.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return BCryptPasswordEncoder.defaultsForSpringSecurity_v5_8();
    }
}
```

---

## 2. Elasticsearch Client Migration

**File**: `src/main/java/com/example/repository/ElasticsearchRepository.java`

**Issue**: Uses deprecated `RestHighLevelClient`

**Suggested Fix**:
```java
// Update dependency
// Remove: org.elasticsearch.client:elasticsearch-rest-high-level-client
// Add: co.elastic.clients:elasticsearch-java:8.11.0

// Configuration
@Configuration
public class ElasticsearchConfig {

    @Bean
    public ElasticsearchClient elasticsearchClient() {
        RestClient restClient = RestClient.builder(
            HttpHost.create("localhost:9200")
        ).build();

        ElasticsearchTransport transport = new RestClientTransport(
            restClient, new JacksonJsonpMapper()
        );

        return new ElasticsearchClient(transport);
    }
}

// Repository update
@Repository
public class ElasticsearchRepository {

    private final ElasticsearchClient client;

    public SearchResponse<Document> search(String index, String field, String value) {
        return client.search(s -> s
            .index(index)
            .query(q -> q
                .match(m -> m.field(field).query(value))
            ),
            Document.class
        );
    }
}
```

---

## 3. Native Library Compatibility

**File**: `src/main/java/com/example/native/NativeLibrary.java`

**Issue**: Uses JNI native methods that may not be compatible with JDK 21

**Suggested Fix**:
- Rebuild native library with JDK 21 toolchain
- Update any architecture-specific code
- Test thoroughly on target platform

---

## 4. Deprecated API Usage

**File**: `src/main/java/com/example/service/ReportService.java`
**Line**: 67

**Issue**: Uses `CrudRepository#findById` returning `Optional`

**Suggested Fix**: No change needed - API is compatible but verify behavior

---

## 5. Third-Party Library Compatibility

**Dependency**: `com.example:legacy-lib:1.0.0`

**Issue**: This library does not support Spring Boot 3.x

**Suggested Fix**:
- Check for Spring Boot 3 compatible version
- Consider alternative libraries:
  - For legacy-lib functionality, consider: {alternativeLibrary}
- Contact vendor for upgrade timeline
- If no alternative, consider forking and updating

---

## 6. Docker Configuration

**File**: `Dockerfile`

**Issue**: Base image uses Java 8

**Current**:
```dockerfile
FROM openjdk:8-jdk-alpine
```

**Suggested Fix**:
```dockerfile
FROM eclipse-temurin:21-jdk-alpine

# Or for JRE only:
FROM eclipse-temurin:21-jre-alpine

# Enable virtual threads (optional, JDK 21+)
ENV JAVA_OPTS="-XX:+UseZGC --enable-preview"
```

---

## Summary

| Category | Count |
|----------|-------|
| Security configs | {count} |
| Elasticsearch | {count} |
| Native libraries | {count} |
| Deprecated APIs | {count} |
| Third-party libs | {count} |
| Docker configs | {count} |

---
*Generated by upgrade-spring-boot3 skill*
```

---

## 6. Error Handling

### 6.1 Error Code Definitions

| Code | Description | Resolution |
|------|-------------|------------|
| `E001` | JDK 21 not installed | Install JDK 21 and configure JAVA_HOME |
| `E002` | Build tool version too low | Upgrade Maven to 3.9+ or Gradle to 8+ |
| `E003` | Uncommitted changes exist | Commit or stash current changes |
| `E004` | Dependency download failed | Check network and repository configuration |
| `E005` | Compilation failed | Review error log, fix manually |
| `E006` | Tests failed | Review test cases, update assertions |
| `E007` | OpenRewrite execution failed | Attempt manual migration |
| `E008` | Git operation failed | Check git status and permissions |
| `E009` | File permission denied | Check file permissions |
| `E010` | Out of memory | Increase JVM heap size |

### 6.2 Rollback Strategy

```yaml
rollback_strategy:
  triggers:
    - compilation_failure
    - critical_error
    - user_request
    - test_failure_threshold_exceeded

  steps:
    - name: "Restore files"
      action: "git checkout ."
      description: "Discard all uncommitted changes"

    - name: "Switch branch"
      action: "git checkout {original_branch}"
      description: "Return to original branch"

    - name: "Delete migration branch"
      action: "git branch -D {migration_branch}"
      description: "Remove migration branch"

    - name: "Clean build artifacts"
      action: "mvn clean || gradle clean"
      description: "Remove compiled files"

    - name: "Generate failure report"
      action: "Create failure-report.md with error details"
      description: "Document failure for future reference"

  notifications:
    - type: console
      message: "Rollback completed. See failure-report.md for details."
```

---

## 7. Test Cases

### 7.1 Unit Tests

```java
class UpgradeSpringBoot3SkillTest {

    @Test
    @DisplayName("Should detect Maven project")
    void shouldDetectMavenProject() {
        // Given: Project with pom.xml
        createFile("pom.xml", "<project>...</project>");

        // When
        AnalysisResult result = skill.analyze();

        // Then
        assertThat(result.getBuildTool()).isEqualTo("maven");
    }

    @Test
    @DisplayName("Should count javax imports correctly")
    void shouldCountJavaxImports() {
        // Given: Java files with javax imports
        createJavaFile("Service.java", "import javax.servlet.http.HttpServlet;");

        // When
        AnalysisResult result = skill.analyze();

        // Then
        assertThat(result.getJavaxImportCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should migrate javax.servlet to jakarta.servlet")
    void shouldMigrateJavaxServlet() {
        // Given: File with javax.servlet import
        String original = "import javax.servlet.http.HttpServlet;";
        createJavaFile("Controller.java", original);

        // When
        skill.migrate();

        // Then
        String migrated = readFile("Controller.java");
        assertThat(migrated).contains("import jakarta.servlet.http.HttpServlet;");
    }

    @Test
    @DisplayName("Should update Spring Security config")
    void shouldUpdateSecurityConfig() {
        // Given: Security config with WebSecurityConfigurerAdapter
        String original = """
            public class SecurityConfig extends WebSecurityConfigurerAdapter {
                protected void configure(HttpSecurity http) { }
            }
            """;

        // When
        skill.migrateSecurity();

        // Then
        String migrated = readFile("SecurityConfig.java");
        assertThat(migrated).contains("SecurityFilterChain");
        assertThat(migrated).doesNotContain("WebSecurityConfigurerAdapter");
    }

    @Test
    @DisplayName("Should fail when JDK 21 not available")
    void shouldFailWhenJdk21NotInstalled() {
        // Given: System with JDK 8
        // When/Then
        assertThrows(PrerequisiteNotMetException.class, () -> skill.execute());
    }

    @Test
    @DisplayName("Should detect Elasticsearch High Level Client usage")
    void shouldDetectElasticsearchUsage() {
        // Given: File using RestHighLevelClient
        createJavaFile("ESConfig.java", "RestHighLevelClient client;");

        // When
        AnalysisResult result = skill.analyze();

        // Then
        assertThat(result.hasElasticsearchHighLevelClient()).isTrue();
    }

    @Test
    @DisplayName("Should detect native library usage")
    void shouldDetectNativeLibraries() {
        // Given: File with native method
        createJavaFile("Native.java", "private native void doSomething();");

        // When
        AnalysisResult result = skill.analyze();

        // Then
        assertThat(result.getNativeLibraryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should assess risk level correctly")
    void shouldAssessRiskLevel() {
        // Given: Project with 150 javax imports
        createMultipleJavaFiles(150, "import javax.servlet.*;");

        // When
        AnalysisResult result = skill.analyze();

        // Then
        assertThat(result.getRiskLevel()).isEqualTo("HIGH");
    }
}
```

### 7.2 Integration Test Scenarios

| Scenario | Input | Expected Output |
|----------|-------|-----------------|
| Simple Web Project | Spring Boot 2.7 + JDK 8 | Upgrade successful, tests pass |
| JPA Project | With JPA entities | javax.persistence migrated |
| Security Project | With security config | Migrated to lambda DSL |
| Elasticsearch Project | RestHighLevelClient | Flagged for manual migration |
| Multi-module Project | Parent + child modules | All modules upgraded |
| Native Library Project | JNI usage | HIGH risk, manual review |
| Microservice Project | Spring Cloud | Spring Cloud 2023.x compatible |

---

## 8. Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2026-02-25 | Initial version with basic migration support |
| 1.1.0 | 2026-02-25 | Added comprehensive risk assessment and English documentation |

---

## 9. Reference Resources

- [Spring Boot 3.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.0-Migration-Guide)
- [Spring Boot 3.2 Release Notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.2-Release-Notes)
- [Spring Framework 6.0 Documentation](https://docs.spring.io/spring-framework/reference/6.0/)
- [Spring Security 6 Migration](https://docs.spring.io/spring-security/reference/migration/index.html)
- [Jakarta EE Migration Guide](https://jakarta.ee/resources/jakarta-ee-10-migration-guide/)
- [OpenRewrite Recipes](https://docs.openrewrite.org/running-recipes/recipes/spring/springboot3)
- [Hibernate 6 Migration](https://docs.jboss.org/hibernate/orm/6.0/migration-guide/migration-guide.html)
- [Elasticsearch Java Client](https://www.elastic.co/guide/en/elasticsearch/client/java-api-client/current/index.html)
- [JDK 21 Migration Guide](https://docs.oracle.com/en/java/javase/21/migrate/)
