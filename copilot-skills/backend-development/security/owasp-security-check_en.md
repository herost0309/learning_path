# OWASP Security Check Skill

> Comprehensively check for security vulnerabilities in backend code, following OWASP Top 10 standards

## Trigger Conditions

- Command: `/security-scan`
- Pull Request: opened, synchronize
- Scheduled: Every Monday morning

---

## OWASP Top 10 (2021) Checklist

### A01 - Broken Access Control

#### Checklist

- [ ] Do sensitive endpoints require authentication
- [ ] Is RBAC/ABAC properly implemented
- [ ] Are there any IDOR (Insecure Direct Object Reference) vulnerabilities
- [ ] Do APIs have rate limiting

#### Problematic Code

```java
// Wrong: Missing permission check
@GetMapping("/api/v1/users/{id}")
public UserResponse getUser(@PathVariable Long id) {
    return userService.getById(id);  // Any user can access other users' info
}

// Wrong: Only checks login, not resource ownership
@PutMapping("/api/v1/orders/{id}")
public OrderResponse updateOrder(@PathVariable Long id, @RequestBody OrderRequest request) {
    return orderService.update(id, request);  // User can modify others' orders
}
```

#### Fix

```java
// Correct: Add permission check
@GetMapping("/api/v1/users/{id}")
@PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.id")
public UserResponse getUser(@PathVariable Long id) {
    return userService.getById(id);
}

// Correct: Check resource ownership
@PutMapping("/api/v1/orders/{id}")
public OrderResponse updateOrder(
    @PathVariable Long id,
    @RequestBody OrderRequest request,
    @AuthenticationPrincipal UserDetails user
) {
    Order order = orderService.getById(id);
    if (!order.getUserId().equals(user.getId())) {
        throw new AccessDeniedException("No permission to access this order");
    }
    return orderService.update(id, request);
}

// Use Spring Security configuration
@Bean
public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/api/v1/public/**").permitAll()
            .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
            .requestMatchers("/api/v1/users/**").authenticated()
            .anyRequest().denyAll()
        )
        .addFilterBefore(new RateLimitFilter(), UsernamePasswordAuthenticationFilter.class);
    return http.build();
}
```

---

### A02 - Cryptographic Failures

#### Checklist

- [ ] Are passwords hashed with strong algorithms
- [ ] Is sensitive data encrypted at rest
- [ ] Is HTTPS used for transmission
- [ ] Is key management secure

#### Problematic Code

```java
// Wrong: Storing password in plaintext
user.setPassword(request.getPassword());

// Wrong: Using weak hash
user.setPassword(DigestUtils.md5DigestAsHex(password.getBytes()));

// Wrong: Hardcoded key
private static final String SECRET_KEY = "my-secret-key-12345";

// Wrong: Returning sensitive info in plaintext
@GetMapping("/api/v1/users/{id}/credit-card")
public CreditCardInfo getCreditCard(@PathVariable Long id) {
    return creditCardService.getById(id);  // Returns full card number
}
```

#### Fix

```java
// Correct: Hash password with BCrypt
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);  // cost factor = 12
}

user.setPassword(passwordEncoder.encode(request.getPassword()));

// Correct: Encrypt sensitive data with AES
@Component
public class EncryptionService {

    @Value("${encryption.secret-key}")
    private String secretKey;  // Get from environment variable or key management service

    private final SecretKeySpec keySpec;
    private final Cipher cipher;

    public EncryptionService(@Value("${encryption.secret-key}") String key) {
        byte[] keyBytes = Arrays.copyOf(key.getBytes(), 32);
        this.keySpec = new SecretKeySpec(keyBytes, "AES");
        try {
            this.cipher = Cipher.getInstance("AES/GCM/NoPadding");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public String encrypt(String data) {
        try {
            byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(ByteUtils.concatenate(iv, encrypted));
        } catch (Exception e) {
            throw new EncryptionException("Failed to encrypt data", e);
        }
    }
}

// Correct: Mask sensitive information
public class CreditCardResponse {
    private String maskedNumber;  // "**** **** **** 1234"
    private String cardHolder;
    private String expiryDate;

    public static CreditCardResponse from(CreditCard card) {
        return CreditCardResponse.builder()
            .maskedNumber(maskCardNumber(card.getNumber()))
            .cardHolder(card.getCardHolder())
            .expiryDate(card.getExpiryDate())
            .build();
    }

    private static String maskCardNumber(String number) {
        return "**** **** **** " + number.substring(number.length() - 4);
    }
}
```

---

### A03 - Injection

#### Checklist

- [ ] Are SQL queries parameterized
- [ ] Are there any JPQL/HQL injection vulnerabilities
- [ ] Are there any command injection vulnerabilities
- [ ] Are there any LDAP injection vulnerabilities

#### Problematic Code

```java
// Wrong: SQL string concatenation
@Query(value = "SELECT * FROM users WHERE email = '" + email + "'", nativeQuery = true)
User findByEmail(String email);

// Wrong: Dynamic JPQL construction
public List<User> search(String name) {
    String jpql = "SELECT u FROM User u WHERE u.name = '" + name + "'";
    return entityManager.createQuery(jpql, User.class).getResultList();
}

// Wrong: Command injection
public String executeCommand(String filename) {
    Runtime.getRuntime().exec("cat " + filename);
}
```

#### Fix

```java
// Correct: Use parameterized query
@Query("SELECT u FROM User u WHERE u.email = :email")
Optional<User> findByEmail(@Param("email") String email);

// Correct: Use Criteria API or Specification
public List<User> search(String name) {
    Specification<User> spec = (root, query, cb) ->
        cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%");
    return userRepository.findAll(spec);
}

// Correct: Avoid command injection, use Java API
public String readFile(String filename) {
    // Whitelist validate filename
    if (!isValidFilename(filename)) {
        throw new IllegalArgumentException("Invalid filename");
    }
    Path path = Paths.get(SAFE_DIR, filename).normalize();
    if (!path.startsWith(SAFE_DIR)) {
        throw new SecurityException("Path traversal detected");
    }
    return Files.readString(path);
}
```

---

### A04 - Insecure Design

#### Checklist

- [ ] Are there vulnerabilities in business logic
- [ ] Are there race conditions
- [ ] Do sensitive operations require secondary verification
- [ ] Is there rate limiting

#### Problematic Code

```java
// Wrong: Coupon can be reused
@Transactional
public void applyCoupon(Long orderId, String couponCode) {
    Coupon coupon = couponRepository.findByCode(couponCode);
    Order order = orderRepository.findById(orderId);
    order.setDiscount(coupon.getDiscount());  // No check if already used
}

// Wrong: Balance check and debit not atomic
public void transfer(Long fromId, Long toId, BigDecimal amount) {
    Account from = accountRepository.findById(fromId);
    if (from.getBalance().compareTo(amount) < 0) {
        throw new InsufficientBalanceException();
    }
    // Race condition possible here
    Account to = accountRepository.findById(toId);
    from.setBalance(from.getBalance().subtract(amount));
    to.setBalance(to.getBalance().add(amount));
}
```

#### Fix

```java
// Correct: Check coupon usage status
@Transactional
public void applyCoupon(Long orderId, String couponCode) {
    Coupon coupon = couponRepository.findByCodeForUpdate(couponCode);  // Pessimistic lock
    if (coupon.isUsed()) {
        throw new CouponAlreadyUsedException();
    }

    Order order = orderRepository.findById(orderId);
    order.setDiscount(coupon.getDiscount());
    coupon.setUsed(true);
    coupon.setUsedAt(LocalDateTime.now());
    coupon.setUsedBy(order.getUserId());
}

// Correct: Use database-level atomic operations
@Transactional(isolation = Isolation.SERIALIZABLE)
public void transfer(Long fromId, Long toId, BigDecimal amount) {
    // Lock in fixed order to avoid deadlock
    Account from = accountRepository.findByIdForUpdate(min(fromId, toId));
    Account to = accountRepository.findByIdForUpdate(max(fromId, toId));

    if (from.getId().equals(fromId)) {
        from = accountRepository.findByIdForUpdate(fromId);
        to = accountRepository.findByIdForUpdate(toId);
    } else {
        to = accountRepository.findByIdForUpdate(toId);
        from = accountRepository.findByIdForUpdate(fromId);
    }

    if (from.getBalance().compareTo(amount) < 0) {
        throw new InsufficientBalanceException();
    }

    from.setBalance(from.getBalance().subtract(amount));
    to.setBalance(to.getBalance().add(amount));
}
```

---

### A05 - Security Misconfiguration

#### Checklist

- [ ] Are default accounts disabled
- [ ] Is debug mode disabled
- [ ] Do error messages leak sensitive information
- [ ] Are Actuator endpoints exposed

#### Problematic Code

```yaml
# Wrong: Expose all actuator endpoints
management:
  endpoints:
    web:
      exposure:
        include: "*"

# Wrong: Disable security check
spring:
  security:
    enabled: false

# Wrong: Show detailed error info
server:
  error:
    include-message: always
    include-stacktrace: always
```

#### Fix

```yaml
# Correct: Only expose necessary endpoints
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: when_authorized

# Correct: Production configuration
spring:
  security:
    enabled: true
  jpa:
    show-sql: false
  devtools:
    enabled: false

server:
  error:
    include-message: never
    include-stacktrace: never
    include-binding-errors: never

# Correct: Security headers configuration
server:
  headers:
    x-frame-options: DENY
    x-content-type-options: nosniff
    x-xss-protection: "1; mode=block"
    strict-transport-security: "max-age=31536000; includeSubDomains"
```

```java
// Correct: Global exception handler doesn't leak sensitive info
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception ex) {
        log.error("Unexpected error", ex);

        // Production doesn't return detailed error info
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse.builder()
                .code("INTERNAL_ERROR")
                .message("An unexpected error occurred")
                .traceId(MDC.get("traceId"))
                .build());
    }
}
```

---

### A06 - Vulnerable Components

#### Checklist

- [ ] Do dependencies have known vulnerabilities
- [ ] Are outdated dependency versions in use
- [ ] Are there unused dependencies

#### Configure OWASP Dependency Check

```xml
<plugin>
    <groupId>org.owasp</groupId>
    <artifactId>dependency-check-maven</artifactId>
    <version>8.2.1</version>
    <configuration>
        <failBuildOnCVSS>7</failBuildOnCVSS>
        <suppressionFile>dependency-check-suppressions.xml</suppressionFile>
    </configuration>
    <executions>
        <execution>
            <goals>
                <goal>check</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

---

### A07 - Identification and Authentication Failures

#### Checklist

- [ ] Are password strength requirements sufficient
- [ ] Is there login failure lockout
- [ ] Is session management secure
- [ ] Is multi-factor authentication supported

#### Problematic Code

```java
// Wrong: No login failure limit
@PostMapping("/login")
public AuthResponse login(@RequestBody LoginRequest request) {
    User user = userService.getByEmail(request.getEmail());
    if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
        throw new InvalidCredentialsException();  // Can try infinitely
    }
    return generateToken(user);
}

// Wrong: Session fixation attack
@PostMapping("/login")
public void login(HttpServletRequest request) {
    // No session ID regeneration after login
    request.getSession().setAttribute("user", user);
}
```

#### Fix

```java
// Correct: Login failure limit
@Service
@RequiredArgsConstructor
public class AuthService {

    private final LoginAttemptService loginAttemptService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        String key = getClientIP(httpRequest);

        // Check if blocked
        if (loginAttemptService.isBlocked(key)) {
            throw new AccountLockedException("Account is locked, please try again later");
        }

        User user = userService.getByEmail(request.getEmail());

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            loginAttemptService.loginFailed(key);
            int remaining = loginAttemptService.getRemainingAttempts(key);
            throw new InvalidCredentialsException(
                "Wrong password, remaining attempts: " + remaining
            );
        }

        // Login successful, clear failure record
        loginAttemptService.loginSucceeded(key);

        // Check if MFA is needed
        if (user.isMfaEnabled()) {
            return AuthResponse.requireMfa(user.getId());
        }

        return generateToken(user);
    }
}

// Correct: Session management
@Configuration
public class SessionConfig {

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }
}

// Spring Security configuration
.sessionManagement(session -> session
    .sessionFixation().migrateSession()  // Migrate session after login
    .maximumSessions(1)  // Single device login
    .maxSessionsPreventsLogin(false)  // Kick out old session
    .sessionRegistry(sessionRegistry())
)
```

---

### A08 - Software and Data Integrity Failures

#### Checklist

- [ ] Is CI/CD pipeline secure
- [ ] Is third-party library integrity verified
- [ ] Is there code signing

#### Secure CI/CD Configuration

```yaml
# GitHub Actions security configuration
name: CI/CD Pipeline

on:
  push:
    branches: [main]

jobs:
  build:
    runs-on: ubuntu-latest
    permissions:
      contents: read
      packages: write

    steps:
      - name: Checkout code
        uses: actions/checkout@v4
        with:
          fetch-depth: 0  # Full history for auditing

      - name: Verify commit signatures
        run: |
          git verify-commit HEAD

      - name: Run dependency check
        uses: dependency-check/Dependency-Check_Action@main

      - name: Run SAST
        uses: github/codeql-action/analyze@v2

      - name: Build with provenance
        uses: slsa-framework/slsa-github-generator/.github/workflows/builder_gradle_slsa3.yml@v1.6.0
```

---

### A09 - Security Logging and Monitoring Failures

#### Checklist

- [ ] Are security-related events logged
- [ ] Do logs contain sufficient information
- [ ] Is there an alerting mechanism
- [ ] Is sensitive information masked in logs

#### Security Logging Configuration

```java
@Configuration
public class SecurityLoggingConfig {

    @Bean
    public AuditAware auditAware() {
        return () -> {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.isAuthenticated()) {
                return Optional.of(auth.getName());
            }
            return Optional.of("anonymous");
        };
    }
}

// Audit logging aspect
@Aspect
@Component
@Slf4j
public class AuditLogAspect {

    @AfterReturning(pointcut = "@annotation(auditable)", returning = "result")
    public void logSuccess(JoinPoint jp, Auditable auditable, Object result) {
        AuditLog log = AuditLog.builder()
            .action(auditable.action())
            .resource(auditable.resource())
            .user(getCurrentUser())
            .ipAddress(getClientIP())
            .timestamp(Instant.now())
            .status("SUCCESS")
            .details(maskSensitive(result))
            .build();
        auditLogRepository.save(log);
    }

    @AfterThrowing(pointcut = "@annotation(auditable)", throwing = "ex")
    public void logFailure(JoinPoint jp, Auditable auditable, Exception ex) {
        AuditLog log = AuditLog.builder()
            .action(auditable.action())
            .resource(auditable.resource())
            .user(getCurrentUser())
            .ipAddress(getClientIP())
            .timestamp(Instant.now())
            .status("FAILURE")
            .errorMessage(ex.getMessage())
            .build();
        auditLogRepository.save(log);

        // Send alert
        if (auditable.alertOnFailure()) {
            alertService.sendAlert(log);
        }
    }
}

// Use annotation to mark methods requiring audit
@Auditable(action = "LOGIN", resource = "AUTH", alertOnFailure = true)
public AuthResponse login(LoginRequest request) {
    // ...
}
```

---

### A10 - Server-Side Request Forgery

#### Checklist

- [ ] Are user-provided URLs validated
- [ ] Is access restricted to allowed domains
- [ ] Is access restricted to allowed ports
- [ ] Is access to internal network resources blocked

#### Problematic Code

```java
// Wrong: Directly use user-provided URL
@GetMapping("/fetch")
public String fetchUrl(@RequestParam String url) {
    return restTemplate.getForObject(url, String.class);  // SSRF vulnerability
}
```

#### Fix

```java
// Correct: URL whitelist validation
@Service
public class SafeUrlService {

    private static final Set<String> ALLOWED_HOSTS = Set.of(
        "api.example.com",
        "cdn.example.com"
    );

    private static final Set<Integer> ALLOWED_PORTS = Set.of(80, 443);

    public String fetchUrl(String urlString) {
        try {
            URL url = new URL(urlString);

            // Validate protocol
            if (!Set.of("http", "https").contains(url.getProtocol())) {
                throw new SecurityException("Only HTTP/HTTPS protocols allowed");
            }

            // Validate hostname
            String host = url.getHost();
            if (!ALLOWED_HOSTS.contains(host)) {
                throw new SecurityException("Access to this domain is not allowed");
            }

            // Validate port
            int port = url.getPort() != -1 ? url.getPort() : url.getDefaultPort();
            if (!ALLOWED_PORTS.contains(port)) {
                throw new SecurityException("Access to this port is not allowed");
            }

            // Prevent internal network access
            InetAddress address = InetAddress.getByName(host);
            if (address.isSiteLocalAddress() || address.isLoopbackAddress()) {
                throw new SecurityException("Access to internal network addresses is not allowed");
            }

            // Set timeout
            RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(5000)
                .setSocketTimeout(10000)
                .build();

            // Execute request
            HttpGet request = new HttpGet(urlString);
            request.setConfig(config);

            try (CloseableHttpResponse response = httpClient.execute(request)) {
                return EntityUtils.toString(response.getEntity());
            }

        } catch (Exception e) {
            throw new SecurityException("URL access failed: " + e.getMessage());
        }
    }
}
```

---

## Security Checklist

### Authentication & Authorization

- [ ] All sensitive endpoints require authentication
- [ ] Fine-grained permission control implemented
- [ ] Prevent unauthorized access
- [ ] Session management is secure

### Data Protection

- [ ] Passwords use strong hashing
- [ ] Sensitive data encrypted at rest
- [ ] HTTPS used for transmission
- [ ] Keys stored securely

### Input Validation

- [ ] SQL parameterization
- [ ] XSS protection
- [ ] CSRF protection
- [ ] Secure file upload

### Configuration Security

- [ ] Debug mode disabled
- [ ] Version information hidden
- [ ] Security headers configured
- [ ] Error messages sanitized

### Monitoring & Auditing

- [ ] Security events logged
- [ ] Anomaly alerts
- [ ] Log sanitization
- [ ] Regular audits
