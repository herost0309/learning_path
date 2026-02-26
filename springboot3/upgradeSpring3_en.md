# Spring Boot 2.x to 3.x Upgrade Guide (JDK 8 to JDK 21)

## Overview

This guide provides a comprehensive plan for upgrading a Spring Boot 2.x application running on JDK 8 to Spring Boot 3.x with JDK 21.

---

## Table of Contents

1. [Required Changes](#required-changes)
2. [Risk Assessment](#risk-assessment)
3. [Pre-Upgrade Checklist](#pre-upgrade-checklist)
4. [Detailed Upgrade Steps](#detailed-upgrade-steps)
5. [Post-Upgrade Validation](#post-upgrade-validation)
6. [Common Issues and Solutions](#common-issues-and-solutions)
7. [Rollback Plan](#rollback-plan)
8. [References](#references)

---

## Required Changes

### 1. Version Matrix

| Component | Before | After |
|-----------|--------|-------|
| JDK Version | 8 | 21 (LTS) |
| Spring Boot | 2.x | 3.2.x |
| Spring Framework | 5.x | 6.x |
| Spring Security | 5.x | 6.x |
| Jakarta EE | javax.* | jakarta.* |
| Tomcat | 9.x | 10.x |
| Hibernate | 5.x | 6.x |
| Hibernate Validator | 6.x | 8.x |

### 2. Key Breaking Changes

#### 2.1 Java Namespace Migration

```
javax.* -> jakarta.*
```

**Affected packages:**
| Before | After |
|--------|-------|
| `javax.servlet` | `jakarta.servlet` |
| `javax.persistence` | `jakarta.persistence` |
| `javax.validation` | `jakarta.validation` |
| `javax.annotation` | `jakarta.annotation` |
| `javax.transaction` | `jakarta.transaction` |
| `javax.mail` | `jakarta.mail` |
| `javax.ws.rs` | `jakarta.ws.rs` |
| `javax.jms` | `jakarta.jms` |
| `javax.json` | `jakarta.json` |
| `javax.enterprise` | `jakarta.enterprise` |

#### 2.2 Spring Framework 6.x Changes

- Minimum JDK 17 required (JDK 21 recommended)
- `WebMvcConfigurer` method signature changes
- `CrudRepository` / `JpaRepository` method updates
- AOT compilation support for native images
- Virtual threads support (JDK 21+)

#### 2.3 Spring Security 6.x Changes

- `WebSecurityConfigurerAdapter` deprecated and removed
- Lambda DSL configuration required
- `antMatchers()` -> `requestMatchers()`
- `authorizeRequests()` -> `authorizeHttpRequests()`
- New session management APIs
- CSRF protection enabled by default

#### 2.4 Hibernate 6.x Changes

- `Hibernate` class replaced by `HibernateOrm`
- Dialect auto-detection improvements
- `@GeneratedValue` strategy defaults changed
- JPA 3.1 compliance
- Native query syntax changes

#### 2.5 Configuration Property Changes

```yaml
# Deprecated in Spring Boot 3.x
spring.security.enabled: false  # REMOVED

# New approach
spring.security.filter.dispatcher-types: []
# or configure via SecurityFilterChain
```

### 3. Build Tool Configuration

#### Maven (pom.xml)

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.2.0</version>
</parent>

<properties>
    <java.version>21</java.version>
    <maven.compiler.source>21</maven.compiler.source>
    <maven.compiler.target>21</maven.compiler.target>
</properties>

<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <version>3.12.1</version>
    <configuration>
        <source>21</source>
        <target>21</target>
    </configuration>
</plugin>
```

#### Gradle (build.gradle)

```groovy
plugins {
    id 'org.springframework.boot' version '3.2.0'
    id 'io.spring.dependency-management' version '1.1.4'
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
```

---

## Risk Assessment

### Critical Risk

| Risk | Impact | Mitigation |
|------|--------|------------|
| **javax -> jakarta namespace** | All servlets, filters, JPA entities, validation code, and annotations must be updated | Use OpenRewrite for automated migration; manual verification required |
| **Third-party library incompatibility** | Libraries may not support Spring Boot 3.x / JDK 21 | Verify ALL dependencies; contact vendors; plan fallbacks |
| **Custom Spring Security configs** | Complete rewrite required; authentication may break | Review ALL security configurations; test thoroughly |
| **Database driver compatibility** | Older drivers incompatible with JDK 21 | Test and upgrade drivers; verify connection pools |
| **Native JNI libraries** | Native libraries may not work with JDK 21 | Rebuild or find compatible versions |

### High Risk

| Risk | Impact | Mitigation |
|------|--------|------------|
| **Elasticsearch client migration** | High Level REST Client removed; must migrate to Java API Client | Complete rewrite of ES queries required |
| **MongoDB driver changes** | Synchronous driver deprecated | Migrate to reactive or new sync driver |
| **Spring Batch 5.x changes** | Job configurations may fail | Review and update batch jobs |
| **Spring Cloud version mismatch** | Services may not communicate | Update ALL services together |
| **Reflection access restrictions** | JDK module system blocks reflective access | Add `--add-opens` JVM flags |
| **Serialization changes** | Jackson configuration may need updates | Test all serialization/deserialization |
| **File encoding change** | JDK 18+ defaults to UTF-8 | Verify file handling; add explicit encoding |

### Medium Risk

| Risk | Impact | Mitigation |
|------|--------|------------|
| **Deprecated method removals** | Compilation errors | Use IDE to identify and fix |
| **Configuration property changes** | Application may not start | Review application.yml/properties |
| **Testing framework changes** | Tests may fail | Update test configurations |
| **AOP/proxy behavior changes** | Runtime issues | Thorough testing required |
| **Cache provider changes** | Caffeine/Redis config changes | Update cache configurations |
| **Kafka client upgrade** | Consumer/producer config changes | Verify Kafka integration |
| **Micrometer metrics changes** | Monitoring dashboards may break | Update metric names and configs |
| **WebSocket configuration** | Endpoint registration changes | Update WebSocket configs |
| **Spring Session changes** | Session management may fail | Review session configuration |
| **Scheduled task changes** | Cron expressions may behave differently | Test all scheduled jobs |

### Low Risk

| Risk | Impact | Mitigation |
|------|--------|------------|
| **Logging format changes** | Log parsing may break | Update log patterns |
| **Actuator endpoint changes** | Monitoring may break | Update monitoring configs |
| **Default port changes** | Minor configuration updates | Verify server.port |
| **Banner changes** | Cosmetic only | Update banner.txt if custom |
| **Dependency management** | Transitive version changes | Review dependency tree |

### Often Overlooked Risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| **JSP support** | JSP requires Jakarta Servlet API | Add jakarta.servlet.jsp dependency |
| **Custom annotations** | May use javax packages | Update annotation imports |
| **AspectJ weaving** | LTW configuration may need updates | Verify AspectJ version compatibility |
| **Docker base images** | Java 8 images won't work | Update Dockerfile to use JDK 21 image |
| **CI/CD pipelines** | Build agents may not have JDK 21 | Update build infrastructure |
| **IDE compatibility** | Older IDEs may not support JDK 21 | Update IDE (IntelliJ 2023.2+, Eclipse 2023-09+) |
| **Code coverage tools** | JaCoCo may need version update | Use JaCoCo 0.8.11+ |
| **Static analysis tools** | SonarQube/SpotBugs may need updates | Update tool versions |
| **Memory/GC settings** | JDK 21 has different defaults | Review and update JVM options |
| **Virtual threads** | New feature may affect behavior | Test with/without virtual threads |

---

## Pre-Upgrade Checklist

### Environment Preparation

- [ ] Install JDK 21 (LTS)
- [ ] Set `JAVA_HOME` to JDK 21
- [ ] Update IDE to support JDK 21 (IntelliJ IDEA 2023.2+, Eclipse 2023-09+, VS Code with extensions)
- [ ] Update Maven to 3.9.6+ or Gradle to 8.5+
- [ ] Backup current codebase and database
- [ ] Ensure all tests pass in current version
- [ ] Document current dependency versions
- [ ] Create rollback plan
- [ ] Notify team and stakeholders
- [ ] Schedule upgrade during low-traffic period

### Code Analysis Tasks

- [ ] Run Spring Boot 2.x to 3.x migration report
- [ ] Count all `javax.*` imports by package
- [ ] List all third-party dependencies with versions
- [ ] Check for deprecated API usage
- [ ] Review custom configurations
- [ ] Identify native JNI library usage
- [ ] Find custom Security configurations
- [ ] Locate JPA entity classes
- [ ] Check for JSP files
- [ ] Identify scheduled tasks
- [ ] Review WebSocket configurations
- [ ] List all external service integrations

### Infrastructure Tasks

- [ ] Update Docker base images
- [ ] Update CI/CD pipelines for JDK 21
- [ ] Verify build agent JDK versions
- [ ] Update deployment scripts
- [ ] Review monitoring and alerting
- [ ] Plan database migration if needed

---

## Detailed Upgrade Steps

### Phase 1: Preparation & Analysis

#### Step 1.1: Create Migration Branch

```bash
git checkout -b upgrade/spring-boot-3
git push -u origin upgrade/spring-boot-3
```

#### Step 1.2: Add Migration Analysis Dependency

```xml
<!-- Add temporarily to pom.xml -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-properties-migrator</artifactId>
    <scope>runtime</scope>
</dependency>
```

Run the application and review migration warnings in logs.

#### Step 1.3: Install OpenRewrite (Recommended)

**Maven:**
```xml
<plugin>
    <groupId>org.openrewrite.maven</groupId>
    <artifactId>rewrite-maven-plugin</artifactId>
    <version>5.23.0</version>
    <configuration>
        <activeRecipes>
            <recipe>org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_2</recipe>
        </activeRecipes>
    </configuration>
</plugin>
```

**Gradle:**
```groovy
plugins {
    id("org.openrewrite.rewrite") version("6.8.4")
}

rewrite {
    activeRecipe("org.openrewrite.java.spring.boot3.UpgradeSpringBoot_3_2")
}

dependencies {
    rewrite("org.openrewrite.recipe:rewrite-spring:5.5.0")
}
```

---

### Phase 2: JDK Upgrade

#### Step 2.1: Update Build Configuration

**Maven pom.xml:**
```xml
<properties>
    <java.version>21</java.version>
    <maven.compiler.source>21</maven.compiler.source>
    <maven.compiler.target>21</maven.compiler.target>
</properties>
```

**Gradle build.gradle:**
```groovy
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}
```

#### Step 2.2: Handle JDK-Specific Changes

1. **Remove Nashorn references** (removed in JDK 15):
```java
// REMOVE any usage of:
// javax.script.ScriptEngine with "nashorn"
// jdk.nashorn.api.scripting.*
```

2. **Update sun.misc.Unsafe usage**:
```java
// Replace sun.misc.Unsafe with java.lang.invoke.VarHandle
// or java.lang.foreign.MemorySegment (JDK 21)
```

3. **Review internal API usage**:
```bash
# Find potentially problematic code
grep -r "sun\." --include="*.java" .
grep -r "com.sun\." --include="*.java" .
```

4. **Add JVM flags for reflection** (if needed):
```bash
--add-opens java.base/java.lang=ALL-UNNAMED
--add-opens java.base/java.util=ALL-UNNAMED
```

---

### Phase 3: Spring Boot 3.x Migration

#### Step 3.1: Run OpenRewrite

```bash
# Maven
mvn rewrite:run

# Gradle
./gradlew rewriteRun
```

#### Step 3.2: Manual javax -> jakarta Migration

**Find and replace patterns:**

| Find | Replace |
|------|---------|
| `import javax.servlet.` | `import jakarta.servlet.` |
| `import javax.persistence.` | `import jakarta.persistence.` |
| `import javax.validation.` | `import jakarta.validation.` |
| `import javax.annotation.` | `import jakarta.annotation.` |
| `import javax.transaction.` | `import jakarta.transaction.` |
| `import javax.mail.` | `import jakarta.mail.` |
| `import javax.ws.rs.` | `import jakarta.ws.rs.` |
| `import javax.jms.` | `import jakarta.jms.` |
| `import javax.json.` | `import jakarta.json.` |
| `import javax.enterprise.` | `import jakarta.enterprise.` |
| `@javax.persistence.` | `@jakarta.persistence.` |
| `@javax.validation.` | `@jakarta.validation.` |
| `@javax.annotation.` | `@jakarta.annotation.` |

**Note:** Also check XML files, YAML files, and properties files.

#### Step 3.3: Update Spring Boot Parent

```xml
<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.2.0</version>
    <relativePath/>
</parent>
```

#### Step 3.4: Update Spring Cloud (if used)

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>org.springframework.cloud</groupId>
            <artifactId>spring-cloud-dependencies</artifactId>
            <version>2023.0.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

#### Step 3.5: Update Database Dependencies

```xml
<!-- Hibernate 6.x -->
<dependency>
    <groupId>org.hibernate.orm</groupId>
    <artifactId>hibernate-core</artifactId>
    <version>6.4.0.Final</version>
</dependency>

<!-- MySQL -->
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <version>8.2.0</version>
</dependency>

<!-- PostgreSQL -->
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <version>42.7.1</version>
</dependency>

<!-- HikariCP -->
<dependency>
    <groupId>com.zaxxer</groupId>
    <artifactId>HikariCP</artifactId>
    <version>5.1.0</version>
</dependency>
```

---

### Phase 4: Security Configuration Update

#### Step 4.1: Rewrite Security Configuration

**Before (Spring Security 5.x):**
```java
@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
            .authorizeRequests()
                .antMatchers("/public/**").permitAll()
                .antMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            .and()
            .formLogin()
                .loginPage("/login")
                .permitAll()
            .and()
            .logout()
                .permitAll();
    }

    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.userDetailsService(userDetailsService).passwordEncoder(passwordEncoder());
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

**After (Spring Security 6.x):**
```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final UserDetailsService userDetailsService;

    public SecurityConfig(UserDetailsService userDetailsService) {
        this.userDetailsService = userDetailsService;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/public/**").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            )
            .formLogin(form -> form
                .loginPage("/login")
                .permitAll()
            )
            .logout(logout -> logout
                .permitAll()
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

#### Step 4.2: Update Method Security

**Before:**
```java
@EnableGlobalMethodSecurity(prePostEnabled = true, securedEnabled = true)
```

**After:**
```java
@EnableMethodSecurity(prePostEnabled = true, securedEnabled = true)
```

#### Step 4.3: Update CSRF Configuration

```java
// If you need to disable CSRF (not recommended for production)
@Bean
public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .csrf(csrf -> csrf.disable())
        // ... other config
    return http.build();
}
```

---

### Phase 5: Data Access Layer Updates

#### Step 5.1: Update JPA Configuration

**application.yml:**
```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false
    properties:
      hibernate:
        format_sql: true
        dialect: org.hibernate.dialect.MySQLDialect  # or your dialect
        jdbc:
          batch_size: 50
```

#### Step 5.2: Update Entity Classes

```java
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String username;

    @Enumerated(EnumType.STRING)
    private UserStatus status;
}
```

#### Step 5.3: Update Repository Interfaces

```java
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    @Query("SELECT u FROM User u WHERE u.email = :email")
    Optional<User> findByEmail(@Param("email") String email);
}
```

---

### Phase 6: Web Layer Updates

#### Step 6.1: Update Controller Imports

```java
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
```

#### Step 6.2: Update Filter Configurations

```java
@Configuration
public class FilterConfig {

    @Bean
    public FilterRegistrationBean<RequestLoggingFilter> loggingFilter() {
        FilterRegistrationBean<RequestLoggingFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new RequestLoggingFilter());
        registration.addUrlPatterns("/api/*");
        registration.setOrder(1);
        return registration;
    }
}
```

#### Step 6.3: Update Validation

```java
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@Validated
public class UserController {

    @PostMapping
    public ResponseEntity<User> create(@Valid @RequestBody UserCreateRequest request) {
        // ...
    }
}

public record UserCreateRequest(
    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be between 3 and 50 characters")
    String username,

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    String email
) {}
```

---

### Phase 7: Elasticsearch Migration (Critical)

#### Step 7.1: Update Dependencies

```xml
<!-- REMOVE old client -->
<!--
<dependency>
    <groupId>org.elasticsearch.client</groupId>
    <artifactId>elasticsearch-rest-high-level-client</artifactId>
</dependency>
-->

<!-- ADD new client -->
<dependency>
    <groupId>co.elastic.clients</groupId>
    <artifactId>elasticsearch-java</artifactId>
    <version>8.11.0</version>
</dependency>
```

#### Step 7.2: Rewrite Query Code

**Before (High Level REST Client):**
```java
SearchRequest searchRequest = new SearchRequest("products");
SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
sourceBuilder.query(QueryBuilders.matchQuery("name", searchTerm));
searchRequest.source(sourceBuilder);
SearchResponse response = client.search(searchRequest, RequestOptions.DEFAULT);
```

**After (Java API Client):**
```java
SearchResponse<Product> response = client.search(s -> s
    .index("products")
    .query(q -> q
        .match(m -> m
            .field("name")
            .query(searchTerm)
        )
    ),
    Product.class
);
```

---

### Phase 8: Testing Updates

#### Step 8.1: Update Test Dependencies

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>

<dependency>
    <groupId>org.junit.jupiter</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

#### Step 8.2: Update Test Classes

```java
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Test
    @DisplayName("Should create user successfully")
    void shouldCreateUser() {
        // test code
    }
}
```

#### Step 8.3: Update MockMvc Tests

```java
@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturnUserById() throws Exception {
        mockMvc.perform(get("/api/users/1"))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.username").value("testuser"));
    }
}
```

---

### Phase 9: Third-Party Dependency Updates

| Dependency | Minimum Version | Notes |
|------------|-----------------|-------|
| Lombok | 1.18.30 | Compatible with JDK 21 |
| MapStruct | 1.5.5.Final | Update for Jakarta |
| SpringDoc OpenAPI | 2.3.0 | Replaces Swagger |
| Liquibase | 4.25.0 | Jakarta namespace |
| Flyway | 10.4.0 | JDK 21 compatible |
| Caffeine | 3.1.8 | Cache library |
| Resilience4j | 3.0.2 | Circuit breaker |
| Jackson | 2.16.0 | JSON processing |
| JaCoCo | 0.8.11 | Code coverage |

---

## Post-Upgrade Validation

### Compilation Check

```bash
mvn clean compile
# or
./gradlew clean compileJava
```

### Test Execution

```bash
mvn test
# or
./gradlew test
```

### Integration Testing Checklist

- [ ] Application starts successfully
- [ ] Database connectivity works
- [ ] All REST endpoints respond correctly
- [ ] Authentication/Authorization works
- [ ] File upload/download works
- [ ] Scheduled jobs execute
- [ ] Email sending works
- [ ] External API integrations work
- [ ] Logging works correctly
- [ ] Metrics and monitoring work
- [ ] Cache operations work
- [ ] Message queue operations work (if applicable)
- [ ] WebSocket connections work
- [ ] Session management works
- [ ] Search functionality works (Elasticsearch)
- [ ] Background jobs complete successfully

### Performance Testing

- [ ] Run load tests
- [ ] Compare response times with previous version
- [ ] Monitor memory usage (JDK 21 GC behavior)
- [ ] Check for memory leaks
- [ ] Verify connection pool behavior
- [ ] Test under production-like load

---

## Common Issues and Solutions

### Issue 1: ClassNotFoundException for javax.*

**Symptoms:**
```
java.lang.ClassNotFoundException: javax.servlet.http.HttpServlet
```

**Solution:**
Global find and replace `javax.*` with `jakarta.*`. Check:
- Java source files
- XML configuration files
- YAML/properties files
- JSP files

### Issue 2: Spring Security Configuration Errors

**Symptoms:**
```
The method configure(HttpSecurity) is undefined for the type WebSecurityConfigurerAdapter
```

**Solution:**
Rewrite security configuration using lambda DSL with `SecurityFilterChain` bean.

### Issue 3: Hibernate Dialect Not Found

**Symptoms:**
```
org.hibernate.boot.registry.selector.spi.StrategySelectionException: Unable to resolve name [org.hibernate.dialect.MySQL5InnoDBDialect]
```

**Solution:**
Explicitly configure dialect:
```yaml
spring:
  jpa:
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQLDialect
```

### Issue 4: Bean Creation Errors

**Symptoms:**
```
Error creating bean with name 'xxx': Circular dependency
```

**Solution:**
Resolve circular dependencies using `@Lazy`:
```java
@Autowired
@Lazy
private SomeService someService;
```

### Issue 5: Validation Not Working

**Symptoms:**
Validation annotations ignored

**Solution:**
1. Add `spring-boot-starter-validation` dependency
2. Ensure imports are `jakarta.validation.*`
3. Add `@Validated` to class

### Issue 6: Swagger UI Not Accessible

**Symptoms:**
```
404 Not Found for /swagger-ui.html
```

**Solution:**
```xml
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.3.0</version>
</dependency>
```

New URL: `http://localhost:8080/swagger-ui/index.html`

### Issue 7: Tests Failing with Context Loading Errors

**Symptoms:**
```
Failed to load ApplicationContext for test
```

**Solution:**
- Update test annotations
- Ensure jakarta namespace is used
- Check test configuration files

### Issue 8: Elasticsearch Client Errors

**Symptoms:**
```
ClassNotFoundException: org.elasticsearch.client.RestHighLevelClient
```

**Solution:**
Migrate to `elasticsearch-java` client with complete API rewrite.

### Issue 9: Reflection Access Errors

**Symptoms:**
```
InaccessibleObjectException: Unable to make field accessible
```

**Solution:**
Add JVM flags:
```bash
--add-opens java.base/java.lang=ALL-UNNAMED
--add-opens java.base/java.util=ALL-UNNAMED
```

### Issue 10: Character Encoding Issues

**Symptoms:**
Garbled text in responses or database

**Solution:**
Explicitly set encoding:
```yaml
server:
  servlet:
    encoding:
      charset: UTF-8
      enabled: true
      force: true
```

---

## Rollback Plan

If critical issues are found:

### Immediate Rollback

```bash
# 1. Switch back to main branch
git checkout main

# 2. Delete migration branch (if not needed)
git branch -D upgrade/spring-boot-3

# 3. Restore database from backup (if schema changed)
mysql -u root -p database_name < backup_$(date +%Y%m%d).sql

# 4. Redeploy previous version
./deploy.sh previous-version
```

### Documentation

1. Document all encountered issues
2. Note which fixes were attempted
3. Record error messages and stack traces
4. Plan next migration attempt

---

## References

- [Spring Boot 3.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.0-Migration-Guide)
- [Spring Boot 3.2 Release Notes](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-3.2-Release-Notes)
- [Spring Framework 6.0 What's New](https://docs.spring.io/spring-framework/reference/6.0/whatsnew.html)
- [Spring Framework 6.1 What's New](https://docs.spring.io/spring-framework/reference/6.1/whatsnew.html)
- [Jakarta EE Migration Guide](https://jakarta.ee/resources/jakarta-ee-10-migration-guide/)
- [OpenRewrite Spring Boot 3 Migration](https://docs.openrewrite.org/running-recipes/recipes/spring/springboot3)
- [Spring Security 6 Migration](https://docs.spring.io/spring-security/reference/migration/index.html)
- [Hibernate 6 Migration Guide](https://docs.jboss.org/hibernate/orm/6.0/migration-guide/migration-guide.html)
- [Elasticsearch Java Client Migration](https://www.elastic.co/guide/en/elasticsearch/client/java-api-client/current/migrate-hlrc.html)

---

## Document History

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-02-25 | Claude | Initial creation |
| 2.0 | 2026-02-25 | Claude | Added comprehensive risk assessment, additional breaking changes, often overlooked risks |
