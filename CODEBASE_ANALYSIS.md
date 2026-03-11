# Login Service - Comprehensive Codebase Analysis

## 1. OVERALL PROJECT STRUCTURE & TECHNOLOGY STACK

### Language & Framework
- **Language**: Scala 2.12/2.13
- **Build Tool**: SBT (Scala Build Tool)
- **Web Framework**: Spring Boot 2.7.8
- **Security**: Spring Security 5.7.6
- **Project Structure**: Multi-module SBT project with 3 modules:
  1. **api** - Main REST service with authentication/JWT logic
  2. **clientLibrary** - Client library for token validation
  3. **examples** - Example usage

### Key Technologies
- **JWT Signing**: JJWT 0.11.5 (Java JWT library) with RS256 algorithm
- **Key Management**: Nimbus JOSE+JWT 9.31
- **LDAP**: Spring Security LDAP
- **Kerberos**: Spring Security Kerberos (1.0.1.RELEASE)
- **AWS Integration**: AWS SDK (Secrets Manager, SSM Parameter Store)
- **Config**: PureConfig 0.17.2 (YAML-based)
- **API Documentation**: SpringDoc OpenAPI UI 1.6.14 (Swagger)

### Build Files
- **Primary**: `/Users/ab006hm/Projects/login-service/build.sbt` (80 lines)
- **Dependencies**: `/Users/ab006hm/Projects/login-service/project/Dependencies.scala`
- **Plugins**: `/Users/ab006hm/Projects/login-service/project/plugins.sbt`

---

## 2. AUTHENTICATION MECHANISMS & HOW THEY WORK

### Multiple Auth Providers (Pluggable Architecture)
The service uses an **ordered provider pattern** allowing multiple auth methods with priority ordering:

#### A. **Config-Based Users Authentication**
**File**: `/Users/ab006hm/Projects/login-service/api/src/main/scala/za/co/absa/loginsvc/rest/provider/ConfigUsersAuthenticationProvider.scala`

**Class**: `ConfigUsersAuthenticationProvider extends AuthenticationProvider`

- Simple hardcoded username/password pairs defined in YAML config
- Stores users in a map: `knownUsersMap: Map[String, UserConfig]`
- Authenticates via `UsernamePasswordAuthenticationToken` (Spring Security)
- Returns a `User` principal with username, groups, and optional attributes
- **Use case**: Development/testing or small number of static users

**Flow**:
```
1. Client sends Basic Auth credentials
2. ConfigUsersAuthenticationProvider.authenticate() called
3. Looks up username in knownUsersMap
4. Compares password
5. Returns UsernamePasswordAuthenticationToken with User principal
```

#### B. **Active Directory LDAP Authentication**
**File**: `/Users/ab006hm/Projects/login-service/api/src/main/scala/za/co/absa/loginsvc/rest/provider/ad/ldap/ActiveDirectoryLDAPAuthenticationProvider.scala` (160 lines)

**Class**: `ActiveDirectoryLDAPAuthenticationProvider extends AuthenticationProvider`

- Wraps Spring Security's `ActiveDirectoryLdapAuthenticationProvider`
- Supports AD LDAP queries via LDAPS (typically port 636)
- **Service Account**: Username/password for LDAP queries (configurable via:
  - Inline config (`in-config-account`)
  - AWS Secrets Manager (`aws-secrets-manager-account`)
  - AWS Systems Manager Parameter Store (`aws-systems-manager-account`)
- **Retry Logic**: Optional retry mechanism for LDAP failures (configurable attempts + delay)
- **Custom Attributes**: Maps LDAP fields to JWT claims (e.g., `mail` → `email`, `displayname` → `displayname`)
- **User Details Mapper**: `LDAPUserDetailsContextMapperWithOptions` extracts groups (authority) and custom attributes

**Configuration** (from example.application.yaml):
```yaml
loginsvc:
  rest:
    auth:
      provider:
        ldap:
          order: 2
          domain: "some.domain.com"
          url: "ldaps://some.domain.com:636/"
          searchFilter: "(samaccountname={1})"
          serviceAccount:
            accountPattern: "CN=%s,OU=Users,OU=Organization1,DC=my,DC=domain,DC=com"
            inConfigAccount:
              username: "svc-ldap"
              password: "password"
          attributes:
            mail: "email"
            displayname: "displayname"
```

#### C. **Kerberos SPNEGO Authentication**
**File**: `/Users/ab006hm/Projects/login-service/api/src/main/scala/za/co/absa/loginsvc/rest/provider/kerberos/KerberosSPNEGOAuthenticationProvider.scala`

**Class**: `KerberosSPNEGOAuthenticationProvider`

- Negotiates Kerberos tokens (SPNEGO) in HTTP requests
- Uses **keytab file** for server-side authentication
- Wraps Spring Security Kerberos components:
  - `KerberosServiceAuthenticationProvider`
  - `SunJaasKerberosTicketValidator`
  - `SpnegoAuthenticationProcessingFilter`
- Integrates with LDAP to fetch user details post-Kerberos validation
- **Configuration** (in ActiveDirectoryLDAPConfig):
  ```yaml
  enableKerberos:
    krbFileLocation: "/etc/krb5.conf"
    keytabFileLocation: "/etc/keytab"
    spn: "HTTP/Host"
    debug: true
  ```

### Authentication Manager Setup
**File**: `/Users/ab006hm/Projects/login-service/api/src/main/scala/za/co/absa/loginsvc/rest/AuthManagerConfig.scala`

**Class**: `AuthManagerConfig`

- Creates a `ProviderManager` with **ordered list** of `AuthenticationProvider` instances
- Order determined by `order` field in config (1st, 2nd, etc.)
- Each auth method tried in sequence until one succeeds
- Validates that **at least one** auth method is enabled

### Security Configuration
**File**: `/Users/ab006hm/Projects/login-service/api/src/main/scala/za/co/absa/loginsvc/rest/SecurityConfig.scala`

**Class**: `SecurityConfig extends Configuration`

**Key Components**:
- **Filter Chain**: 
  - Disables CSRF, enables CORS
  - Stateless session management (no server-side sessions)
  - Custom `BasicAuthenticationFilter` with special exception handling
  - Kerberos filter added before BasicAuth if enabled
- **Public Endpoints** (no auth required):
  - `/v3/api-docs*`, `/swagger-ui/**`, `/actuator/**`
  - `/token/refresh` (accepts tokens in body)
  - `/token/public-key`, `/token/public-keys`, `/token/public-key-jwks`
- **Protected Endpoints**: `/token/generate` (requires authentication)
- **Custom Entry Point**: Handles LDAP connection failures (504 status), other failures (401)

---

## 3. TOKEN GENERATION & JWT LOGIC

### Token Generation Flow

**File**: `/Users/ab006hm/Projects/login-service/api/src/main/scala/za/co/absa/loginsvc/rest/service/jwt/JWTService.scala` (323 lines)

**Class**: `JWTService extends Service`

#### Key Methods:

**`generateAccessToken(user: User, isRefresh: Boolean = false): AccessToken`**
- Expires in: configurable (default 15 minutes from example config)
- Claims included:
  - `sub` (subject): username
  - `exp` (expiration): calculated from current time + accessExpTime
  - `iat` (issued at): current time
  - `kid` (key ID): public key thumbprint
  - `groups`: list of user groups (Java List format)
  - `type`: "access" token type
  - **Custom attributes**: Any optional attributes from user (e.g., mail, displayname)
- Signed with: Private RSA key from `primaryKeyPair`

**`generateRefreshToken(user: User): RefreshToken`**
- Expires in: configurable (default 9 hours from example config)
- Claims:
  - `sub`, `exp`, `iat`, `type` ("refresh")
  - **No groups or attributes** (minimal)
- Purpose: Can be exchanged for new access token via `/token/refresh`

**`refreshTokens(accessToken: AccessToken, refreshToken: RefreshToken): (AccessToken, RefreshToken)`**
- Validates **both** tokens against current and previous public keys
- Parses old access token to extract user info
- Searches for user in repositories to verify still exists & get updated info
- Keeps intersection of old groups (only groups from original token)
- Returns newly generated access token + original refresh token

### Key Rotation & Management

**Inline Key Generation** (InMemoryKeyConfig):
- Generates RSA keypair on startup (or on schedule if rotation enabled)
- Stores current and optional previous keypair in memory
- **Key Rotation**: Scheduled via `ScheduledThreadPoolExecutor`:
  - Rotates every `keyRotationTime` (e.g., 9 hours)
  - Maintains previous key for `keyLayOverTime` (e.g., 15 mins overlap)
  - Phases out old key after `keyPhaseOutTime` (e.g., 15 mins)
  
**AWS Secrets Manager Keys** (AwsSecretsManagerKeyConfig):
- Fetches public/private key pair from AWS Secrets Manager
- Looks for "AWSCURRENT" (primary) and "AWSPREVIOUS" (secondary) versions
- Polls for updates every `pollTime`
- Respects key lay-over and phase-out periods
- See: `/Users/ab006hm/Projects/login-service/api/src/main/scala/za/co/absa/loginsvc/rest/config/jwt/AwsSecretsManagerKeyConfig.scala` (160 lines)

### Token Response Structure

**File**: `/Users/ab006hm/Projects/login-service/api/src/main/scala/za/co/absa/loginsvc/rest/model/TokensWrapper.scala`

```scala
case class TokensWrapper(
  @JsonProperty("token") token: String,      // Access token (JWT)
  @JsonProperty("refresh") refresh: String   // Refresh token (JWT)
)

case class AccessToken(token: String) extends Token
case class RefreshToken(token: String) extends Token

object Token {
  object TokenType extends Enumeration {
    val Access = Value("access")
    val Refresh = Value("refresh")
  }
}
```

**JSON Response Example**:
```json
{
  "token": "eyJhbGc...(access token JWT)...zI1NiJ9",
  "refresh": "eyJhbGc...(refresh token JWT)...zI1NiJ9"
}
```

---

## 4. ROUTING/API LAYER - ALL ENDPOINTS

**File**: `/Users/ab006hm/Projects/login-service/api/src/main/scala/za/co/absa/loginsvc/rest/controller/TokenController.scala` (261 lines)

**Base Path**: `/token`

### POST /token/generate
- **Authentication**: Basic Auth or Negotiate (Kerberos)
- **Security**: `@SecurityRequirement(name = "basicAuth")` + `@SecurityRequirement(name = "negotiate")`
- **Query Parameters**:
  - `group-prefixes` (optional): CSV list of group prefixes to filter JWT groups
  - `case-sensitive` (optional, default=false): Case sensitivity for prefix matching
- **Returns**: `TokensWrapper` (access + refresh tokens)
- **Status**: 200 OK | 401 Unauthorized
- **Implementation**: Calls `jwtService.generateAccessToken()` and `jwtService.generateRefreshToken()`

### GET /token/experimental/get-generate (Experimental)
- **Same as POST /token/generate** but via GET method
- **Requires**: `loginsvc.rest.experimental.enabled=true`
- **Returns**: `TokensWrapper`

### POST /token/refresh
- **Authentication**: None required (tokens in body)
- **Request Body**: `TokensWrapper` containing both access and refresh tokens
- **Returns**: `TokensWrapper` (new access token + same refresh token)
- **Status**: 200 OK | 401 Unauthorized (expired/invalid) | 400 Bad Request (malformed)
- **Implementation**: Calls `jwtService.refreshTokens()`

### GET /token/public-key
- **Authentication**: None (public endpoint)
- **Returns**: `PublicKey` object with Base64-encoded current public key
- **Response**:
```json
{
  "key": "MIIBIjANBgkqhkiG9w0BA..."
}
```

### GET /token/public-keys
- **Authentication**: None (public endpoint)
- **Returns**: `PublicKeySet` with list of current + previous public keys
- **Response**:
```json
{
  "keys": [
    { "key": "MIIBIjANBgkqhkiG9w0BA..." },
    { "key": "MIIBIjANBgkqhkiG9w0BA..." }
  ]
}
```

### GET /token/public-key-jwks
- **Authentication**: None (public endpoint)
- **Returns**: JWKS (JSON Web Key Set) format per RFC 7517
- **Response** (standard JWKS format):
```json
{
  "keys": [
    {
      "kty": "RSA",
      "n": "0vx7agoebGcQSuuPiLJXZptN9...",
      "e": "AQAB",
      "alg": "RS256",
      "kid": "abc123"
    }
  ]
}
```

### Other Endpoints (Actuator)
- **GET /actuator/health** - Health check endpoint
- **GET /actuator/info** - Application info
- **GET /v3/api-docs** - OpenAPI 3 JSON
- **GET /v3/api-docs.yaml** - OpenAPI 3 YAML
- **GET /swagger-ui.html** - Swagger UI

---

## 5. THIRD-PARTY AUTH INTEGRATIONS

### **Current Integrations**:
1. **Active Directory LDAP** - Full support
2. **Kerberos/SPNEGO** - Full support (integrates with LDAP)
3. **AWS Services**:
   - AWS Secrets Manager (for storing JWT keys and service account credentials)
   - AWS Systems Manager Parameter Store (for service account credentials)
   - AWS STS/SSO (dependencies included but not actively used)

### **NO Current OAuth2 / SAML / Azure AD Integration**
- The search `grep -r "oauth\|saml\|azuread\|entra"` returned **no results** in the Scala code
- Only dependency on OAuth2 is `spring-security-oauth2-jose` (for JWT decoding utilities)

### **Extensibility Notes**:
The architecture supports adding a new provider. Template would be:
1. Create new `class MyAuthProvider extends AuthenticationProvider`
2. Create config case class `MyAuthConfig extends ConfigValidatable with ConfigOrdering`
3. Add to `AuthManagerConfig.createAuthProviders()` pattern matching
4. Update `ConfigProvider` to load config
5. Implement `authenticate(authentication: Authentication): Authentication`

---

## 6. CONFIGURATION PATTERNS - EXTERNAL CREDENTIALS/SECRETS

### Main Configuration Loading
**File**: `/Users/ab006hm/Projects/login-service/api/src/main/scala/za/co/absa/loginsvc/rest/config/provider/ConfigProvider.scala` (107 lines)

**Class**: `ConfigProvider extends JwtConfigProvider with AuthConfigProvider with ExperimentalRestConfigProvider`

- Loads YAML configuration via **PureConfig**
- Spring looks for YAML in standard locations (environment variable or command-line arg)
- Spring property: `spring.config.location`

### Configuration File Structure
**Example**: `/Users/ab006hm/Projects/login-service/api/src/main/resources/example.application.yaml`

```yaml
loginsvc:
  rest:
    # JWT Configuration
    jwt:
      generate-in-memory:  # OR aws-secrets-manager
        access-exp-time: 15min
        refresh-exp-time: 9h
        key-rotation-time: 9h
        key-lay-over-time: 15min
        key-phase-out-time: 15min
        alg-name: "RS256"
    
    # Authentication Configuration
    auth:
      provider:
        users:
          order: 1
          known-users:
            - username: "user1"
              password: "password1"  # PLAINTEXT (security risk!)
              groups: []
              attributes:
                displayname: "User One"
        
        ldap:
          order: 2
          domain: "some.domain.com"
          url: "ldaps://some.domain.com:636/"
          searchFilter: "(samaccountname={1})"
          serviceAccount:
            accountPattern: "CN=%s,OU=Users,DC=domain,DC=com"
            # Option 1: Inline credentials
            inConfigAccount:
              username: "svc-ldap"
              password: "password"
            # Option 2: AWS Secrets Manager
            # awsSecretsManagerAccount:
            #   secretName: "my-ldap-secret"
            #   region: "us-east-1"
            #   usernameFieldName: "username"
            #   passwordFieldName: "password"
            # Option 3: AWS Systems Manager
            # awsSystemsManagerAccount:
            #   parameter: "/ldap/svc-account"
            #   decryptIfSecure: true
            #   usernameFieldName: "username"
            #   passwordFieldName: "password"
```

### AWS Credential Management

**AWS Secrets Manager** (for JWT keys):
- **Fetched via**: `AwsSecretsUtils.fetchSecret()`
- **Location**: `/Users/ab006hm/Projects/login-service/api/src/main/scala/za/co/absa/loginsvc/utils/AwsSecretsUtils.scala`
- **Uses**: `DefaultCredentialsProvider` (AWS SDK standard credential chain)
  - Environment variables: `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`
  - IAM role (if running on EC2/ECS)
  - AWS credentials file (~/.aws/credentials)
- **Secret Format**: JSON with fields configured via `privateKeyFieldName`, `publicKeyFieldName`
- **Version Staging**: Supports "AWSCURRENT" and "AWSPREVIOUS" version stages

**AWS Systems Manager Parameter Store** (for service account credentials):
- **Utility**: `AwsSsmUtils` (not shown in current files, but referenced in config)
- **Enables**: Secure parameter retrieval with optional decryption

### Validation Framework
**File**: `/Users/ab006hm/Projects/login-service/api/src/main/scala/za/co/absa/loginsvc/rest/config/validation/`

All config classes implement `ConfigValidatable` trait:
- `validate(): ConfigValidationResult` (returns Success or Error)
- `throwErrors()` - throws ConfigValidationException on failure
- Validation called at startup via `ConfigProvider`

---

## 7. DATA MODELS FOR USERS & SESSIONS

### User Model
**File**: `/Users/ab006hm/Projects/login-service/api/src/main/scala/za/co/absa/loginsvc/model/User.scala`

```scala
case class User(
  name: String,                                    // Username
  groups: Seq[String],                           // Assigned groups/roles
  optionalAttributes: Map[String, Option[AnyRef]] // Key-value pairs (email, displayname, etc.)
) {
  def filterGroupsByPrefixes(prefixes: Set[String], caseSensitive: Boolean): User = {
    // Filters groups by prefix (for JWT claims filtering)
  }
}
```

### User Configuration
**File**: `/Users/ab006hm/Projects/login-service/api/src/main/scala/za/co/absa/loginsvc/rest/config/auth/UsersConfig.scala`

```scala
case class UserConfig(
  username: String,
  password: String,                    // PLAINTEXT in config (security consideration)
  groups: Array[String],
  attributes: Option[Map[String, String]]
)

case class UsersConfig(
  knownUsers: Array[UserConfig],
  order: Int
)
```

### LDAP Configuration
**File**: `/Users/ab006hm/Projects/login-service/api/src/main/scala/za/co/absa/loginsvc/rest/config/auth/ActiveDirectoryLDAPConfig.scala`

```scala
case class ActiveDirectoryLDAPConfig(
  domain: String,
  url: String,
  searchFilter: String,
  order: Int,
  serviceAccount: ServiceAccountConfig,
  enableKerberos: Option[KerberosConfig],
  ldapRetry: Option[LdapRetryConfig],
  attributes: Option[Map[String, String]]  // LDAP field -> JWT claim mapping
)

case class ServiceAccountConfig(
  accountPattern: String,  // CN=%s,OU=...,DC=...
  inConfigAccount: Option[InConfigAccountConfig],
  awsSecretsManagerAccount: Option[AwsSecretsLdapUserConfig],
  awsSystemsManagerAccount: Option[AwsSystemsManagerLdapUserConfig]
)
```

### Spring Security Integration
- **Principal**: `User` (stored in `Authentication.getPrincipal()`)
- **Authorities**: Groups mapped to `SimpleGrantedAuthority`
- **Session**: STATELESS (no server-side session storage)
- **Token in JWT**: All user info persisted in JWT claims

### User Repositories (for User Lookup)
**File**: `/Users/ab006hm/Projects/login-service/api/src/main/scala/za/co/absa/loginsvc/rest/service/search/`

**Interface**: `UserRepository.searchForUser(username: String): Option[User]`

**Implementations**:
1. `UsersFromConfigRepository` - Queries hardcoded users
2. `LdapUserRepository` - Queries LDAP directory
3. `DefaultUserRepositories` - Combines both, tries in order

**Used for**: Refreshing user info during token refresh (verify user still exists, get updated groups)

---

## 8. DEPENDENCIES DECLARATION

### Build File
**Location**: `/Users/ab006hm/Projects/login-service/build.sbt`

**Structure**:
```scala
lazy val parent = (project in file("."))
  .aggregate(api, clientLibrary, examples)

lazy val api = project
  .settings(libraryDependencies ++= apiDependencies)
  .enablePlugins(TomcatPlugin, AutomateHeaderPlugin, FilteredJacocoAgentPlugin)

lazy val clientLibrary = project
  .settings(libraryDependencies ++= clientLibDependencies)
  .enablePlugins(AutomateHeaderPlugin, FilteredJacocoAgentPlugin)

lazy val examples = project
  .dependsOn(clientLibrary)
  .settings(libraryDependencies ++= exampleDependencies)
```

### Dependency Definitions
**File**: `/Users/ab006hm/Projects/login-service/project/Dependencies.scala` (149 lines)

**Key Dependencies**:

| Component | Library | Version |
|-----------|---------|---------|
| **Web** | spring-boot-starter-web | 2.7.8 |
| **Security** | spring-boot-starter-security | 2.7.8 |
| **LDAP** | spring-security-ldap | 5.7.6 |
| **Kerberos** | spring-security-kerberos-web/client | 1.0.1.RELEASE |
| **JWT Signing** | jjwt-api/impl/jackson | 0.11.5 |
| **Key Management** | nimbus-jose-jwt | 9.31 |
| **JWT Decoding** | spring-security-oauth2-jose | 5.7.6 |
| **AWS** | awssdk-secretsmanager, awssdk-ssm, awssdk-sts | 2.20.x |
| **Config** | pureconfig, pureconfig-yaml | 0.17.2 |
| **API Docs** | springdoc-openapi-ui | 1.6.14 |
| **Scala** | jackson-module-scala, scala-java8-compat | 2.14.2 / 0.9.0 |

**Test Dependencies**:
- scalatest 3.2.15
- spring-boot-starter-test 2.7.8
- spring-security-test 5.7.6
- scalamock 5.2.0

---

## 9. TEST PATTERNS - HOW AUTH FLOWS ARE TESTED

### JWT Service Tests
**File**: `/Users/ab006hm/Projects/login-service/api/src/test/scala/za/co/absa/loginsvc/rest/service/jwt/JWTServiceTest.scala`

**Test Class**: `JWTServiceTest extends AnyFlatSpec with BeforeAndAfterEach with Matchers`

**Key Test Cases**:
```scala
// Test data setup
private val userWithoutEmailAndGroups: User = User(
  name = "user2",
  groups = Seq.empty,
  optionalAttributes = Map.empty
)

// Access token generation & validation
it should "return an access JWT that is verifiable by `publicKey`" in {
  val jwt = jwtService.generateAccessToken(userWithoutGroups)
  val parsedJWT = parseJWT(jwt)
  assert(parsedJWT.isSuccess)
}

// Token claims validation
it should "return an access JWT with subject equal to User.name and has type access" in {
  val jwt = jwtService.generateAccessToken(userWithoutGroups)
  parsedJWT.map(_.getBody.getSubject) shouldBe userWithoutGroups.name
  parsedJWT.map(_.getBody.get("type", classOf[String])) shouldBe "access"
}

// Custom attributes in token
it should "return an access JWT with email claim equal to User.email if it is not None"
```

**Helper Method**:
```scala
private def parseJWT(jwt: Token, publicKey: PublicKey = jwtService.publicKeys._1): Try[Jws[Claims]] = Try {
  Jwts.parserBuilder().setSigningKey(publicKey).build().parseClaimsJws(jwt.token)
}
```

### Token Controller Tests
**File**: `/Users/ab006hm/Projects/login-service/api/src/test/scala/za/co/absa/loginsvc/rest/controller/TokenControllerTest.scala`

**Test Class**: `TokenControllerTest extends AnyFlatSpec with ControllerIntegrationTestBase`

**Setup**:
```scala
@WebMvcTest(controllers = Array(classOf[TokenController]))
@Import(Array(classOf[ConfigProvider], classOf[SecurityConfig], classOf[RestResponseEntityExceptionHandler], classOf[AuthManagerConfig]))

@MockBean private var jwtService: JWTService = _
```

**Test Cases**:
```scala
// Basic token generation with mocked service
it should "return tokens generated by mocked JWTService for the basic-auth authenticated user" in {
  when(jwtService.generateAccessToken(FakeAuthentication.fakeUser)).thenReturn(fakeAccessJwt)
  when(jwtService.generateRefreshToken(FakeAuthentication.fakeUser)).thenReturn(fakeRefreshJwt)
  
  mockMvc.perform(
    post("/token/generate")
      .`with`(authentication(FakeAuthentication.fakeUserAuthentication))
      .contentType(MediaType.APPLICATION_JSON)
  )
  .andExpect(status.isOk)
  .andExpect(content.json(expectedJsonBody))
}

// Group prefix filtering
it should "return tokens... with group-prefixes (single)" in {
  // Tests group filtering functionality
}
```

### Authentication Provider Tests
**File**: `/Users/ab006hm/Projects/login-service/api/src/test/scala/za/co/absa/loginsvc/rest/provider/ConfigUsersAuthenticationProviderTest.scala`

**Pattern**: Spring Security's `AuthenticationProvider` testing

```scala
// Test valid credentials
// Test invalid credentials
// Test missing user
// Test group assignment
```

### LDAP Provider Tests
**File**: `/Users/ab006hm/Projects/login-service/api/src/test/scala/za/co/absa/loginsvc/rest/provider/ad/ldap/ActiveDirectoryLDAPAuthenticationProviderTest.scala`

- Uses test LDAP fixtures/mocks
- Tests domain handling, retry logic, attribute mapping

### Configuration Tests
**Location**: `/Users/ab006hm/Projects/login-service/api/src/test/scala/za/co/absa/loginsvc/rest/config/`

- `UsersConfigTest.scala` - Validates user config parsing
- `ActiveDirectoryLDAPConfigTest.scala` - Validates LDAP config
- `KerberosConfigTest.scala` - Validates Kerberos config
- `AwsSecretsLdapUserConfigTest.scala` - Tests AWS secret config
- `InMemoryKeyConfigTest.scala` - Tests in-memory key config
- `AwsSecretsManagerKeyConfigTest.scala` - Tests AWS secret key config

**Test Resource Config**: `/Users/ab006hm/Projects/login-service/api/src/test/resources/application.yaml`

---

## 10. MIDDLEWARE & FILTER CHAINS

### Security Filter Chain
**File**: `/Users/ab006hm/Projects/login-service/api/src/main/scala/za/co/absa/loginsvc/rest/SecurityConfig.scala`

**Filter Order** (executed top to bottom):

1. **Kerberos SPNEGO Filter** (if enabled)
   - Class: `SpnegoAuthenticationProcessingFilter`
   - Configured in: `KerberosSPNEGOAuthenticationProvider.spnegoAuthenticationProcessingFilter`
   - Processes: Negotiate header tokens
   - Validates: Kerberos tickets against keytab

2. **Basic Auth Filter**
   - Class: `BasicAuthenticationFilter`
   - Processes: Authorization: Basic header
   - Parses: Base64-encoded username:password
   - Creates: `UsernamePasswordAuthenticationToken`

3. **Authentication Manager**
   - Delegates to ordered `AuthenticationProvider` list
   - Each provider tries in sequence
   - First successful auth wins

4. **Authorization Filter** (Spring Security)
   - Enforces path-based authorization rules
   - Public paths: API docs, swagger, actuator, public keys, token refresh
   - Protected paths: Require authentication (`/token/generate`)

### Exception Handling
**File**: `/Users/ab006hm/Projects/login-service/api/src/main/scala/za/co/absa/loginsvc/rest/RestResponseEntityExceptionHandler.scala`

**Custom Auth Entry Point** (in SecurityConfig):
```scala
private def customAuthenticationEntryPoint: AuthenticationEntryPoint =
  (request, response, authException) => {
    authException match {
      case LdapConnectionException(msg, _) =>
        response.setStatus(HttpServletResponse.SC_GATEWAY_TIMEOUT)  // 504
        response.write(s"""{"error": "LDAP connection failed: $msg"}""")
      case _ =>
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED)     // 401
        response.write(s"""{"error": "User login error"}""")
    }
  }
```

**Special Error Handling**:
- LDAP connection failures → 504 Gateway Timeout
- Auth failures → 401 Unauthorized
- Expired JWT → 401 Unauthorized
- Malformed JWT → 400 Bad Request

### CORS & CSRF Configuration
- **CSRF**: Disabled (API is stateless, uses tokens)
- **CORS**: Enabled (allows cross-origin requests)

### Session Management
- **Policy**: `SessionCreationPolicy.STATELESS`
- **Effect**: No `JSESSIONID` cookies, no server-side session data
- **Token Storage**: JWT tokens sent via Authorization header or request body

### MVC Configuration
**File**: `/Users/ab006hm/Projects/login-service/api/src/main/scala/za/co/absa/loginsvc/rest/WebMvcConfig.scala`

- Likely configures JSON serialization, CORS details, etc.

---

## SUMMARY TABLE: Key Files by Feature

| Feature | File Path | Key Classes | LOC |
|---------|-----------|-------------|-----|
| **JWT Generation** | `.../service/jwt/JWTService.scala` | `JWTService` | 323 |
| **Token Endpoints** | `.../controller/TokenController.scala` | `TokenController` | 261 |
| **Config Users Auth** | `.../provider/ConfigUsersAuthenticationProvider.scala` | `ConfigUsersAuthenticationProvider` | 60 |
| **LDAP Auth** | `.../provider/ad/ldap/ActiveDirectoryLDAPAuthenticationProvider.scala` | `ActiveDirectoryLDAPAuthenticationProvider` | 160 |
| **Kerberos Auth** | `.../provider/kerberos/KerberosSPNEGOAuthenticationProvider.scala` | `KerberosSPNEGOAuthenticationProvider` | 73 |
| **Security Config** | `.../SecurityConfig.scala` | `SecurityConfig` | 102 |
| **Auth Manager** | `.../AuthManagerConfig.scala` | `AuthManagerConfig` | 71 |
| **Configuration** | `.../config/provider/ConfigProvider.scala` | `ConfigProvider` | 107 |
| **User Model** | `.../model/User.scala` | `User` | 31 |
| **Token Models** | `.../model/TokensWrapper.scala` | `TokensWrapper`, `AccessToken`, `RefreshToken` | 59 |
| **In-Memory Keys** | `.../config/jwt/InMemoryKeyConfig.scala` | `InMemoryKeyConfig` | 61 |
| **AWS Secret Keys** | `.../config/jwt/AwsSecretsManagerKeyConfig.scala` | `AwsSecretsManagerKeyConfig` | 160 |
| **AWS Utils** | `.../utils/AwsSecretsUtils.scala` | `AwsSecretsUtils` | 80 |

---

## DESIGN PATTERNS OBSERVED

1. **Provider Pattern**: Multiple auth providers with pluggable architecture
2. **Strategy Pattern**: Swappable key configs (in-memory vs AWS)
3. **Factory Pattern**: `AuthManagerConfig` creates providers based on config
4. **Repository Pattern**: `UserRepository` for user lookup abstraction
5. **Decorator Pattern**: `LdapUserDetailsContextMapperWithOptions` wraps base mapper
6. **Configuration Validation**: Custom trait-based validation with result merging
7. **Lazy Evaluation**: User repositories tried lazily via Iterator
8. **Scheduled Tasks**: `ScheduledThreadPoolExecutor` for key rotation

