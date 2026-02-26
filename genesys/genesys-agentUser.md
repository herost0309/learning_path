# Genesys Cloud Coaching APIs - Java SDK 文档

## 概述

本文档提供了使用 Java SDK 访问 Genesys Cloud Platform APIs 的完整文档，重点关注 Coaching（辅导）功能。

**SDK 版本:** com.mypurecloud.sdk.v2:platform-client-v2:247.0.0

**资源链接:**
- 文档: https://mypurecloud.github.io/platform-client-sdk-java/
- 源码: https://github.com/MyPureCloud/platform-client-sdk-java
- Maven: https://mvnrepository.com/artifact/com.mypurecloud/platform-client-v2

---

## 安装

### Maven 依赖

```xml
<dependency>
    <groupId>com.mypurecloud</groupId>
    <artifactId>platform-client-v2</artifactId>
    <version>247.0.0</version>
</dependency>
```

---

## 身份认证

Java SDK 支持多种身份认证方式来访问 Genesys Cloud APIs。

### 1. 客户端凭证模式（推荐用于服务端应用）

```java
import com.mypurecloud.sdk.v2.ApiClient;
import com.mypurecloud.sdk.v2.ApiResponse;
import com.mypurecloud.sdk.v2.Configuration;
import com.mypurecloud.sdk.v2.PureCloudRegionHosts;
import com.mypurecloud.sdk.v2.model.AuthResponse;
import com.mypurecloud.sdk.v2.api.UsersApi;
import com.mypurecloud.sdk.v2.model.UserEntityListing;

String clientId = "YourOAuthClientID";
String clientSecret = "YourOAuthClientSecret";

// 设置区域
PureCloudRegionHosts region = PureCloudRegionHosts.us_east_1;

ApiClient apiClient = ApiClient.Builder.standard().withBasePath(region).build();
ApiResponse<AuthResponse> authResponse = apiClient.authorizeClientCredentials(clientId, clientSecret);

// 使用 ApiClient 实例
Configuration.setDefaultApiClient(apiClient);

// 创建 API 实例并发起已认证的 API 请求
UsersApi apiInstance = new UsersApi();
UserEntityListing response = apiInstance.getUsers(null, null, null, null, null, null, null);
```

### 2. 授权码流程

```java
String clientId = "YourOAuthClientID";
String clientSecret = "YourOAuthClientSecret";
String authorizationCode = "YourAuthorizationCode";
String redirectUri = "YourRedirectURI";

PureCloudRegionHosts region = PureCloudRegionHosts.us_east_1;

ApiClient apiClient = ApiClient.Builder.standard().withBasePath(region).build();
ApiResponse<AuthResponse> authResponse = apiClient.authorizeCodeAuthorization(
    clientId, clientSecret, authorizationCode, redirectUri
);
System.out.println("认证成功。访问令牌将在 " +
    authResponse.getBody().getExpires_in() + " 秒后过期");
```

### 3. PKCE（代码交换证明密钥）

```java
String clientId = "YourOAuthClientID";
String codeVerifier = "YourCodeVerifier";
String authorizationCode = "YourAuthorizationCode";
String redirectUri = "YourRedirectURI";

PureCloudRegionHosts region = PureCloudRegionHosts.us_east_1;

ApiClient apiClient = ApiClient.Builder.standard().withBasePath(region).build();

// 生成 PKCE code verifier 和 challenge
String codeVerifier = apiClient.generatePKCECodeVerifier(128);
String codeChallenge = apiClient.computePKCECodeChallenge(codeVerifier);

ApiResponse<AuthResponse> authResponse = apiClient.authorizePKCE(
    clientId, codeVerifier, authorizationCode, redirectUri
);
```

### 4. SAML2 Bearer 令牌

```java
String clientId = "YourOAuthClientID";
String clientSecret = "YourOAuthClientSecret";
String orgName = "YourOrg";
String encodedSamlAssertion = ""; // Base64 编码的 SAML 断言

PureCloudRegionHosts region = PureCloudRegionHosts.us_east_1;

ApiClient apiClient = ApiClient.Builder.standard().withBasePath(region).build();
ApiResponse<AuthResponse> authResponse = apiClient.authorizeSaml2Bearer(
    clientId, clientSecret, orgName, encodedSamlAssertion
);
```

---

## 区域配置

Genesys Cloud 支持多个区域。请为您的组织设置适当的区域：

```java
// 可用区域：
PureCloudRegionHosts.us_east_1      // 美国东部 (dca)
PureCloudRegionHosts.us_east_2      // 美国东部 (dca)
PureCloudRegionHosts.us_west_2      // 美国西部 (dca)
PureCloudRegionHosts.eu_west_1      // 欧洲西部 (dub)
PureCloudRegionHosts.eu_west_2      // 欧洲西部 (lon)
PureCloudRegionHosts.ap_southeast_2 // 亚太东南 (syd)
PureCloudRegionHosts.ap_northeast_1 // 亚太东北 (tok)
PureCloudRegionHosts.ap_northeast_2 // 亚太东北 (kaw)
PureCloudRegionHosts.sa_east_1      // 南美东部 (sae)
PureCloudRegionHosts.ca_central_1   // 加拿大中部 (cac)

// 或使用自定义基础路径
ApiClient apiClient = ApiClient.Builder.standard()
    .withBasePath("https://api.mypurecloud.ie")
    .build();
```

---

## 构建 API 客户端

### 标准构建器模式

```java
import com.mypurecloud.sdk.v2.ApiClient;
import com.mypurecloud.sdk.v2.Configuration;
import com.mypurecloud.sdk.v2.PureCloudRegionHosts;

PureCloudRegionHosts region = PureCloudRegionHosts.us_east_1;

ApiClient apiClient = ApiClient.Builder.standard()
    .withAccessToken(accessToken)
    .withBasePath(region)
    .withConnectionTimeout(30000)
    .withShouldThrowErrors(true)
    .build();

Configuration.setDefaultApiClient(apiClient);
```

### 配置重试机制

```java
ApiClient.RetryConfiguration retryConfiguration = new ApiClient.RetryConfiguration();
retryConfiguration.setMaxRetryTimeSec(30);
retryConfiguration.setRetryMax(5);

ApiClient apiClient = ApiClient.Builder.standard()
    .withBasePath(region)
    .withRetryConfiguration(retryConfiguration)
    .build();
```

### 配置网关

```java
ApiClient apiClient = ApiClient.Builder.standard()
    .withGateway(
        "mygateway.mydomain.myextension",  // 主机地址
        "https",                            // 协议
        1443,                               // 端口
        "myadditionalpathforlogin",         // 登录路径
        "myadditionalpathforapi"            // API 路径
    )
    .build();
```

---

## 发起 API 请求

### 使用请求构建器（推荐）

```java
import com.mypurecloud.sdk.v2.api.UsersApi;
import com.mypurecloud.sdk.v2.model.UserMe;
import com.mypurecloud.sdk.v2.api.request.GetUsersMeRequest;
import java.util.Collections;

UsersApi usersApi = new UsersApi();

GetUsersMeRequest request = GetUsersMeRequest.builder()
    .withExpand(Collections.singletonList("presence"))
    .build();

UserMe me = usersApi.getUsersMe(request);
System.out.println("你好 " + me.getName());
```

### 使用方法参数

```java
UsersApi usersApi = new UsersApi();
UserMe me = usersApi.getUsersMe(Collections.singletonList("presence"));
System.out.println("你好 " + me.getName());
```

### 获取扩展 HTTP 信息

```java
import com.mypurecloud.sdk.v2.ApiResponse;
import com.mypurecloud.sdk.v2.ApiRequest;

// 使用 WithHttpInfo 方法
ApiResponse<UserMe> meWithHttpInfo = usersApi.getUsersMeWithHttpInfo(new ArrayList<>());
System.out.println(meWithHttpInfo.getHeaders());
System.out.println(meWithHttpInfo.getCorrelationId());
System.out.println(meWithHttpInfo.getBody().getName());

// 使用请求构建器配合 HttpInfo
ApiRequest<Void> getUsersMeRequestWithHttpInfo = GetUsersMeRequest.builder()
    .withExpand(new ArrayList<String>())
    .build()
    .withHttpInfo();

ApiResponse<UserMe> meWithHttpInfo = usersApi.getUsersMe(getUsersMeRequestWithHttpInfo);
```

---

## Coaching 通知

Genesys Cloud 通知系统通过 WebSocket 支持实时辅导相关通知。

### Coaching 通知主题

```
v2.users.{id}.wem.coaching.notification
```

**事件体类:** `WemCoachingUserNotificationTopicCoachingUserNotification`

### 使用 NotificationHandler 处理 Coaching 事件

```java
import com.mypurecloud.sdk.v2.extensions.NotificationHandler;
import com.mypurecloud.sdk.v2.extensions.NotificationListener;
import com.mypurecloud.sdk.v2.extensions.NotificationEvent;
import com.mypurecloud.sdk.v2.model.WemCoachingUserNotificationTopicCoachingUserNotification;

public class CoachingNotificationListener implements
    NotificationListener<WemCoachingUserNotificationTopicCoachingUserNotification> {

    private String userId;

    public CoachingNotificationListener(String userId) {
        this.userId = userId;
        this.topic = "v2.users." + userId + ".wem.coaching.notification";
    }

    private String topic;

    @Override
    public String getTopic() {
        return topic;
    }

    @Override
    public Class<WemCoachingUserNotificationTopicCoachingUserNotification> getEventBodyClass() {
        return WemCoachingUserNotificationTopicCoachingUserNotification.class;
    }

    @Override
    public void onEvent(NotificationEvent<?> event) {
        WemCoachingUserNotificationTopicCoachingUserNotification coachingNotification =
            (WemCoachingUserNotificationTopicCoachingUserNotification) event.getEventBody();

        // 处理辅导通知
        System.out.println("收到用户辅导通知: " + userId);
        // 处理辅导通知数据
    }
}

// 使用示例
NotificationHandler notificationHandler = NotificationHandler.Builder.standard()
    .withNotificationListener(new CoachingNotificationListener("user-id-here"))
    .withAutoConnect(true)
    .build();
```

### 完整的通知处理器配置

```java
import com.mypurecloud.sdk.v2.extensions.NotificationHandler;

// 使用构建器创建通知处理器
NotificationHandler notificationHandler = NotificationHandler.Builder.standard()
    .withWebSocketListener(new MyWebSocketListener())
    .withNotificationListener(new CoachingNotificationListener(userId))
    .withAutoConnect(false)
    .build();

// 或使用默认构造函数
NotificationHandler notificationHandler = new NotificationHandler();
notificationHandler.addSubscription(new CoachingNotificationListener(userId));

// 发送 ping 测试连接
notificationHandler.sendPing();
```

---

## 日志配置

### 编程式日志配置

```java
import com.mypurecloud.sdk.v2.ApiClient;

ApiClient.LoggingConfiguration loggingConfiguration = new ApiClient.LoggingConfiguration();
loggingConfiguration.setLogLevel("trace");      // trace, debug, error, none
loggingConfiguration.setLogFormat("json");      // json, text
loggingConfiguration.setLogRequestBody(true);
loggingConfiguration.setLogResponseBody(true);
loggingConfiguration.setLogToConsole(true);
loggingConfiguration.setLogFilePath("/var/log/javasdk.log");

ApiClient apiClient = ApiClient.Builder.standard()
    .withLoggingConfiguration(loggingConfiguration)
    .build();
```

### 配置文件

**INI 格式 (`~/.genesyscloudjava/config`):**

```ini
[logging]
log_level = trace
log_format = text
log_to_console = false
log_file_path = /var/log/javasdk.log
log_response_body = false
log_request_body = false

[retry]
retry_wait_min = 3
retry_wait_max = 10
retry_max = 5

[reauthentication]
refresh_access_token = true
refresh_token_wait_max = 10

[general]
live_reload_config = true
host = https://api.mypurecloud.com
```

**JSON 格式:**

```json
{
    "logging": {
        "log_level": "trace",
        "log_format": "text",
        "log_to_console": false,
        "log_file_path": "/var/log/javasdk.log",
        "log_response_body": false,
        "log_request_body": false
    },
    "retry": {
        "retry_wait_min": 3,
        "retry_wait_max": 10,
        "retry_max": 5
    },
    "reauthentication": {
        "refresh_access_token": true,
        "refresh_token_wait_max": 10
    },
    "general": {
        "live_reload_config": true,
        "host": "https://api.mypurecloud.com"
    }
}
```

---

## HTTP 连接器

SDK 支持多种 HTTP 连接器：

| 连接器 | 描述 | 提供者类 |
|--------|------|----------|
| Apache | 默认，同步 | `ApacheHttpClientConnectorProvider` |
| Ning | 异步 | `AsyncHttpClientConnectorProvider` |
| OkHTTP | 推荐用于 Android | `OkHttpClientConnectorProvider` |

```java
import com.mypurecloud.sdk.v2.connector.okhttp.OkHttpClientConnectorProvider;

ApiClient apiClient = ApiClient.Builder.standard()
    .withProperty(ApiClientConnectorProperty.CONNECTOR_PROVIDER,
        new OkHttpClientConnectorProvider())
    .build();
```

---

## 最佳实践

### 1. 令牌管理

- 安全存储刷新令牌
- 尽可能使用自动令牌刷新
- 主动监控令牌过期并及时刷新

```java
// 启用自动令牌刷新
ApiClient apiClient = ApiClient.Builder.standard()
    .withBasePath(region)
    .withShouldRefreshAccessToken(true)
    .withRefreshTokenWaitTime(10)
    .build();
```

### 2. 错误处理

```java
import com.mypurecloud.sdk.v2.ApiException;

try {
    UserMe me = usersApi.getUsersMe(request);
} catch (ApiException e) {
    System.err.println("API 错误: " + e.getMessage());
    System.err.println("状态码: " + e.getCode());
    System.err.println("响应体: " + e.getResponseBody());
}
```

### 3. 重试配置

遵循 Genesys Cloud [速率限制](https://developer.genesys.cloud/platform/api/rate-limits) 最佳实践：

```java
ApiClient.RetryConfiguration retryConfiguration = new ApiClient.RetryConfiguration();
retryConfiguration.setMaxRetryTimeSec(30);
retryConfiguration.setRetryMax(5);

ApiClient apiClient = ApiClient.Builder.standard()
    .withRetryConfiguration(retryConfiguration)
    .build();
```

### 4. 连接管理

- 对高并发应用使用连接池
- 设置适当的超时时间
- 优雅地处理连接失败

```java
ApiClient apiClient = ApiClient.Builder.standard()
    .withConnectionTimeout(30000)  // 30 秒
    .withShouldThrowErrors(true)
    .build();
```

### 5. 日志最佳实践

- 避免在生产环境中记录 PII（个人身份信息）数据
- 使用适当的日志级别
- 对文件日志实现日志轮转

```java
ApiClient.LoggingConfiguration loggingConfiguration = new ApiClient.LoggingConfiguration();
loggingConfiguration.setLogLevel("error");  // 生产环境使用 error
loggingConfiguration.setLogRequestBody(false);  // 避免记录 PII
loggingConfiguration.setLogResponseBody(false);
```

### 6. SDK 版本管理

- 保持 SDK 更新到最新版本
- 关注发布说明中的破坏性变更
- 升级时进行充分测试

### 7. 代理配置

```java
import java.net.Proxy;

// 无认证代理
Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("proxy.host", 8080));
ApiClient apiClient = ApiClient.Builder.standard()
    .withProxy(proxy)
    .build();

// 需要认证的代理
ApiClient apiClient = ApiClient.Builder.standard()
    .withAuthenticatedProxy(proxy, "username", "password")
    .build();
```

---

## API 包结构

SDK 在 `com.mypurecloud.sdk.v2.api` 命名空间中提供 API 类：

| API 类 | 描述 |
|--------|------|
| `UsersApi` | 用户管理操作 |
| `CoachingApi` | 辅导预约和提示操作 |
| `NotificationsApi` | 通知渠道管理 |
| `PresenceApi` | 用户状态操作 |
| `ConversationsApi` | 对话管理 |
| `RoutingApi` | 队列和路由操作 |

---

## 导入语句参考

```java
// 核心 SDK 导入
import com.mypurecloud.sdk.v2.ApiClient;
import com.mypurecloud.sdk.v2.ApiException;
import com.mypurecloud.sdk.v2.ApiResponse;
import com.mypurecloud.sdk.v2.Configuration;
import com.mypurecloud.sdk.v2.PureCloudRegionHosts;

// 模型导入
import com.mypurecloud.sdk.v2.model.AuthResponse;
import com.mypurecloud.sdk.v2.model.User;
import com.mypurecloud.sdk.v2.model.UserMe;
import com.mypurecloud.sdk.v2.model.UserEntityListing;

// API 导入
import com.mypurecloud.sdk.v2.api.UsersApi;
import com.mypurecloud.sdk.v2.api.CoachingApi;

// 通知导入
import com.mypurecloud.sdk.v2.extensions.NotificationHandler;
import com.mypurecloud.sdk.v2.extensions.NotificationListener;
import com.mypurecloud.sdk.v2.extensions.NotificationEvent;
```

---

## 其他资源

- **Genesys Cloud 开发者中心:** https://developer.genesys.cloud/
- **API Explorer:** https://developer.genesys.cloud/devguide/api-explorer/
- **授权文档:** https://developer.genesys.cloud/authorization/platform-auth/
- **速率限制指南:** https://developer.genesys.cloud/platform/api/rate-limits
- **开发者社区:** https://community.genesys.com/
- **GitHub 仓库:** https://github.com/MyPureCloud/platform-client-sdk-java
- **Javadoc 下载:** `https://repo1.maven.org/maven2/com/mypurecloud/platform-client-v2/{VERSION}/platform-client-v2-{VERSION}-javadoc.jar`

---

## 预览 API 警告

本 SDK 包含预览 API。这些资源可能随时发生破坏性或非破坏性变更，恕不另行通知。这包括但不限于：
- 更改资源名称
- 更改路径
- 更改约定
- 更改文档
- 完全删除资源

有关预览 API 的完整列表，请参阅: https://developer.genesys.cloud/platform/preview-apis

---

## Android 支持

从 SDK 版本 5.0.1 开始，SDK 可用于 Android。这需要在 Android Studio（2.4 Preview 6 或更高版本）中支持 Java 8。

更多信息请参阅: [Java 8 Language Features Support Update](https://android-developers.googleblog.com/2017/04/java-8-language-features-support-update.html)
