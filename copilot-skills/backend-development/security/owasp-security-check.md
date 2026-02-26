# OWASP 安全检查 Skill

> 全面检查后端代码中的安全漏洞，遵循 OWASP Top 10 标准

## 触发条件

- 命令: `/security-scan`
- Pull Request: opened, synchronize
- 定时任务: 每周一早上

---

## OWASP Top 10 (2021) 检查清单

### A01 - 访问控制失效 (Broken Access Control)

#### 检查项

- [ ] 敏感端点是否需要认证
- [ ] 是否正确实现 RBAC/ABAC
- [ ] 是否存在越权访问（IDOR）
- [ ] API 是否有速率限制

#### 问题代码

```java
// 错误: 缺少权限检查
@GetMapping("/api/v1/users/{id}")
public UserResponse getUser(@PathVariable Long id) {
    return userService.getById(id);  // 任何用户都能访问其他用户信息
}

// 错误: 只检查登录，不检查资源所有权
@PutMapping("/api/v1/orders/{id}")
public OrderResponse updateOrder(@PathVariable Long id, @RequestBody OrderRequest request) {
    return orderService.update(id, request);  // 用户可以修改其他人的订单
}
```

#### 修复方案

```java
// 正确: 添加权限检查
@GetMapping("/api/v1/users/{id}")
@PreAuthorize("hasRole('ADMIN') or #id == authentication.principal.id")
public UserResponse getUser(@PathVariable Long id) {
    return userService.getById(id);
}

// 正确: 检查资源所有权
@PutMapping("/api/v1/orders/{id}")
public OrderResponse updateOrder(
    @PathVariable Long id,
    @RequestBody OrderRequest request,
    @AuthenticationPrincipal UserDetails user
) {
    Order order = orderService.getById(id);
    if (!order.getUserId().equals(user.getId())) {
        throw new AccessDeniedException("无权访问此订单");
    }
    return orderService.update(id, request);
}

// 使用 Spring Security 配置
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

### A02 - 加密失败 (Cryptographic Failures)

#### 检查项

- [ ] 密码是否使用强哈希算法
- [ ] 敏感数据是否加密存储
- [ ] 传输是否使用 HTTPS
- [ ] 密钥管理是否安全

#### 问题代码

```java
// 错误: 明文存储密码
user.setPassword(request.getPassword());

// 错误: 使用弱哈希
user.setPassword(DigestUtils.md5DigestAsHex(password.getBytes()));

// 错误: 硬编码密钥
private static final String SECRET_KEY = "my-secret-key-12345";

// 错误: 敏感信息明文传输
@GetMapping("/api/v1/users/{id}/credit-card")
public CreditCardInfo getCreditCard(@PathVariable Long id) {
    return creditCardService.getById(id);  // 返回完整卡号
}
```

#### 修复方案

```java
// 正确: 使用 BCrypt 哈希密码
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(12);  // cost factor = 12
}

user.setPassword(passwordEncoder.encode(request.getPassword()));

// 正确: 使用 AES 加密敏感数据
@Component
public class EncryptionService {

    @Value("${encryption.secret-key}")
    private String secretKey;  // 从环境变量或密钥管理服务获取

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

// 正确: 脱敏处理敏感信息
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

### A03 - 注入攻击 (Injection)

#### 检查项

- [ ] SQL 查询是否参数化
- [ ] 是否存在 JPQL/HQL 注入
- [ ] 是否存在命令注入
- [ ] 是否存在 LDAP 注入

#### 问题代码

```java
// 错误: SQL 字符串拼接
@Query(value = "SELECT * FROM users WHERE email = '" + email + "'", nativeQuery = true)
User findByEmail(String email);

// 错误: 动态构建 JPQL
public List<User> search(String name) {
    String jpql = "SELECT u FROM User u WHERE u.name = '" + name + "'";
    return entityManager.createQuery(jpql, User.class).getResultList();
}

// 错误: 命令注入
public String executeCommand(String filename) {
    Runtime.getRuntime().exec("cat " + filename);
}
```

#### 修复方案

```java
// 正确: 使用参数化查询
@Query("SELECT u FROM User u WHERE u.email = :email")
Optional<User> findByEmail(@Param("email") String email);

// 正确: 使用 Criteria API 或 Specification
public List<User> search(String name) {
    Specification<User> spec = (root, query, cb) ->
        cb.like(cb.lower(root.get("name")), "%" + name.toLowerCase() + "%");
    return userRepository.findAll(spec);
}

// 正确: 避免命令注入，使用 Java API
public String readFile(String filename) {
    // 白名单验证文件名
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

### A04 - 不安全设计 (Insecure Design)

#### 检查项

- [ ] 业务逻辑是否有漏洞
- [ ] 是否存在竞态条件
- [ ] 敏感操作是否需要二次验证
- [ ] 是否有速率限制

#### 问题代码

```java
// 错误: 优惠券可重复使用
@Transactional
public void applyCoupon(Long orderId, String couponCode) {
    Coupon coupon = couponRepository.findByCode(couponCode);
    Order order = orderRepository.findById(orderId);
    order.setDiscount(coupon.getDiscount());  // 没有检查是否已使用
}

// 错误: 余额检查和扣款不是原子操作
public void transfer(Long fromId, Long toId, BigDecimal amount) {
    Account from = accountRepository.findById(fromId);
    if (from.getBalance().compareTo(amount) < 0) {
        throw new InsufficientBalanceException();
    }
    // 这里可能发生竞态条件
    Account to = accountRepository.findById(toId);
    from.setBalance(from.getBalance().subtract(amount));
    to.setBalance(to.getBalance().add(amount));
}
```

#### 修复方案

```java
// 正确: 检查优惠券使用状态
@Transactional
public void applyCoupon(Long orderId, String couponCode) {
    Coupon coupon = couponRepository.findByCodeForUpdate(couponCode);  // 悲观锁
    if (coupon.isUsed()) {
        throw new CouponAlreadyUsedException();
    }

    Order order = orderRepository.findById(orderId);
    order.setDiscount(coupon.getDiscount());
    coupon.setUsed(true);
    coupon.setUsedAt(LocalDateTime.now());
    coupon.setUsedBy(order.getUserId());
}

// 正确: 使用数据库级别的原子操作
@Transactional(isolation = Isolation.SERIALIZABLE)
public void transfer(Long fromId, Long toId, BigDecimal amount) {
    // 按固定顺序加锁，避免死锁
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

### A05 - 安全配置错误 (Security Misconfiguration)

#### 检查项

- [ ] 是否关闭了默认账户
- [ ] 是否关闭了调试模式
- [ ] 错误信息是否泄露敏感信息
- [ ] Actuator 端点是否暴露

#### 问题代码

```yaml
# 错误: 暴露所有 actuator 端点
management:
  endpoints:
    web:
      exposure:
        include: "*"

# 错误: 关闭安全检查
spring:
  security:
    enabled: false

# 错误: 显示详细错误信息
server:
  error:
    include-message: always
    include-stacktrace: always
```

#### 修复方案

```yaml
# 正确: 只暴露必要的端点
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: when_authorized

# 正确: 生产环境配置
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

# 正确: 安全头配置
server:
  headers:
    x-frame-options: DENY
    x-content-type-options: nosniff
    x-xss-protection: "1; mode=block"
    strict-transport-security: "max-age=31536000; includeSubDomains"
```

```java
// 正确: 全局异常处理不泄露敏感信息
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception ex) {
        log.error("Unexpected error", ex);

        // 生产环境不返回详细错误信息
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

### A06 - 易受攻击组件 (Vulnerable Components)

#### 检查项

- [ ] 依赖是否有已知漏洞
- [ ] 是否使用过时的依赖版本
- [ ] 是否有未使用的依赖

#### 配置 OWASP Dependency Check

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

### A07 - 认证失败 (Identification and Authentication Failures)

#### 检查项

- [ ] 密码强度要求是否足够
- [ ] 是否有登录失败锁定
- [ ] Session 管理是否安全
- [ ] 是否支持多因素认证

#### 问题代码

```java
// 错误: 没有登录失败限制
@PostMapping("/login")
public AuthResponse login(@RequestBody LoginRequest request) {
    User user = userService.getByEmail(request.getEmail());
    if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
        throw new InvalidCredentialsException();  // 可以无限尝试
    }
    return generateToken(user);
}

// 错误: Session 固定攻击
@PostMapping("/login")
public void login(HttpServletRequest request) {
    // 登录后没有重新生成 Session ID
    request.getSession().setAttribute("user", user);
}
```

#### 修复方案

```java
// 正确: 登录失败限制
@Service
@RequiredArgsConstructor
public class AuthService {

    private final LoginAttemptService loginAttemptService;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        String key = getClientIP(httpRequest);

        // 检查是否被锁定
        if (loginAttemptService.isBlocked(key)) {
            throw new AccountLockedException("账户已锁定，请稍后再试");
        }

        User user = userService.getByEmail(request.getEmail());

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            loginAttemptService.loginFailed(key);
            int remaining = loginAttemptService.getRemainingAttempts(key);
            throw new InvalidCredentialsException(
                "密码错误，剩余尝试次数: " + remaining
            );
        }

        // 登录成功，清除失败记录
        loginAttemptService.loginSucceeded(key);

        // 检查是否需要 MFA
        if (user.isMfaEnabled()) {
            return AuthResponse.requireMfa(user.getId());
        }

        return generateToken(user);
    }
}

// 正确: Session 管理
@Configuration
public class SessionConfig {

    @Bean
    public HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }
}

// Spring Security 配置
.sessionManagement(session -> session
    .sessionFixation().migrateSession()  // 登录后迁移 Session
    .maximumSessions(1)  // 单设备登录
    .maxSessionsPreventsLogin(false)  // 踢出旧 Session
    .sessionRegistry(sessionRegistry())
)
```

---

### A08 - 软件完整性失败 (Software and Data Integrity Failures)

#### 检查项

- [ ] CI/CD 管道是否安全
- [ ] 是否验证第三方库完整性
- [ ] 是否有代码签名

#### 安全 CI/CD 配置

```yaml
# GitHub Actions 安全配置
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
          fetch-depth: 0  # 完整历史，便于审计

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

### A09 - 日志监控失败 (Security Logging and Monitoring Failures)

#### 检查项

- [ ] 是否记录安全相关事件
- [ ] 日志是否包含足够信息
- [ ] 是否有告警机制
- [ ] 敏感信息是否脱敏

#### 安全日志配置

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

// 审计日志切面
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

        // 发送告警
        if (auditable.alertOnFailure()) {
            alertService.sendAlert(log);
        }
    }
}

// 使用注解标记需要审计的方法
@Auditable(action = "LOGIN", resource = "AUTH", alertOnFailure = true)
public AuthResponse login(LoginRequest request) {
    // ...
}
```

---

### A10 - 服务端请求伪造 (Server-Side Request Forgery)

#### 检查项

- [ ] 是否验证用户提供的 URL
- [ ] 是否限制可访问的域名
- [ ] 是否限制可访问的端口
- [ ] 是否阻止访问内网资源

#### 问题代码

```java
// 错误: 直接使用用户提供的 URL
@GetMapping("/fetch")
public String fetchUrl(@RequestParam String url) {
    return restTemplate.getForObject(url, String.class);  // SSRF 漏洞
}
```

#### 修复方案

```java
// 正确: URL 白名单验证
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

            // 验证协议
            if (!Set.of("http", "https").contains(url.getProtocol())) {
                throw new SecurityException("只允许 HTTP/HTTPS 协议");
            }

            // 验证主机名
            String host = url.getHost();
            if (!ALLOWED_HOSTS.contains(host)) {
                throw new SecurityException("不允许访问此域名");
            }

            // 验证端口
            int port = url.getPort() != -1 ? url.getPort() : url.getDefaultPort();
            if (!ALLOWED_PORTS.contains(port)) {
                throw new SecurityException("不允许访问此端口");
            }

            // 防止访问内网
            InetAddress address = InetAddress.getByName(host);
            if (address.isSiteLocalAddress() || address.isLoopbackAddress()) {
                throw new SecurityException("不允许访问内网地址");
            }

            // 设置超时
            RequestConfig config = RequestConfig.custom()
                .setConnectTimeout(5000)
                .setSocketTimeout(10000)
                .build();

            // 执行请求
            HttpGet request = new HttpGet(urlString);
            request.setConfig(config);

            try (CloseableHttpResponse response = httpClient.execute(request)) {
                return EntityUtils.toString(response.getEntity());
            }

        } catch (Exception e) {
            throw new SecurityException("URL 访问失败: " + e.getMessage());
        }
    }
}
```

---

## 安全检查清单

### 认证与授权

- [ ] 所有敏感端点需要认证
- [ ] 实现细粒度的权限控制
- [ ] 防止越权访问
- [ ] Session 管理安全

### 数据保护

- [ ] 密码使用强哈希
- [ ] 敏感数据加密存储
- [ ] 传输使用 HTTPS
- [ ] 密钥安全存储

### 输入验证

- [ ] SQL 参数化
- [ ] XSS 防护
- [ ] CSRF 防护
- [ ] 文件上传安全

### 配置安全

- [ ] 关闭调试模式
- [ ] 隐藏版本信息
- [ ] 安全头配置
- [ ] 错误信息脱敏

### 监控审计

- [ ] 记录安全事件
- [ ] 异常行为告警
- [ ] 日志脱敏
- [ ] 定期审计
