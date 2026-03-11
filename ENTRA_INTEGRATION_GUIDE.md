# MS Entra (Azure AD) Token Exchange Integration Design Guide

Based on the login-service codebase analysis, this guide provides a concrete blueprint for adding MS Entra support.

## ARCHITECTURE OVERVIEW

### Design Approach
Add MS Entra as a **fourth authentication provider** following the existing pluggable architecture:

```
Authentication Flow:
  Client → REST API → AuthenticationManager → [Providers in order]
                                              1. ConfigUsers (if order=1)
                                              2. LDAP (if order=2)
                                              3. Kerberos (if order=3)
                                              4. MS Entra (if order=4) [NEW]
```

---

## IMPLEMENTATION PLAN

### Phase 1: Configuration Classes

**New File**: `api/src/main/scala/za/co/absa/loginsvc/rest/config/auth/MsEntraConfig.scala`

```scala
package za.co.absa.loginsvc.rest.config.auth

import za.co.absa.loginsvc.rest.config.validation.{ConfigValidatable, ConfigValidationException, ConfigValidationResult}
import za.co.absa.loginsvc.rest.config.validation.ConfigValidationResult.{ConfigValidationError, ConfigValidationSuccess}

case class MsEntraConfig(
  order: Int,
  tenantId: String,                           // Azure tenant ID / directory ID
  clientId: String,                           // Application (client) ID
  clientSecret: String,                       // Client secret (from config or AWS Secrets)
  redirectUri: String,                        // Must match registered redirect URI in Azure
  discoveryUrl: Option[String] = None,        // Override for testing (defaults to Microsoft standard)
  scope: String = "https://graph.microsoft.com/.default",
  attributes: Option[Map[String, String]] = None  // MS Graph -> JWT claim mapping
) extends ConfigValidatable with ConfigOrdering {

  def throwErrors(): Unit = this.validate().throwOnErrors()

  override def validate(): ConfigValidationResult = {
    if (order > 0) {
      val results = Seq(
        Option(tenantId)
          .map(_ => ConfigValidationSuccess)
          .getOrElse(ConfigValidationError(ConfigValidationException("tenantId is empty"))),
        
        Option(clientId)
          .map(_ => ConfigValidationSuccess)
          .getOrElse(ConfigValidationError(ConfigValidationException("clientId is empty"))),
        
        Option(clientSecret)
          .map(_ => ConfigValidationSuccess)
          .getOrElse(ConfigValidationError(ConfigValidationException("clientSecret is empty"))),
        
        Option(redirectUri)
          .map(_ => ConfigValidationSuccess)
          .getOrElse(ConfigValidationError(ConfigValidationException("redirectUri is empty")))
      )

      results.foldLeft[ConfigValidationResult](ConfigValidationSuccess)(ConfigValidationResult.merge)
    } else ConfigValidationSuccess
  }
}

// Optional: AWS Secrets Manager variant for clientSecret
case class MsEntraAwsSecretsConfig(
  order: Int,
  tenantId: String,
  clientId: String,
  awsSecretsManagerConfig: AwsSecretReference,  // secret name, region, field name
  redirectUri: String,
  discoveryUrl: Option[String] = None,
  scope: String = "https://graph.microsoft.com/.default",
  attributes: Option[Map[String, String]] = None
) extends ConfigValidatable with ConfigOrdering { ... }

case class AwsSecretReference(
  secretName: String,
  region: String,
  clientSecretFieldName: String
)
```

---

### Phase 2: HTTP Client & Token Exchange

**New File**: `api/src/main/scala/za/co/absa/loginsvc/rest/provider/entra/MsEntraClient.scala`

```scala
package za.co.absa.loginsvc.rest.provider.entra

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.slf4j.LoggerFactory
import za.co.absa.loginsvc.rest.config.auth.MsEntraConfig
import scala.io.Source
import scala.util.{Try, Using}

class MsEntraClient(config: MsEntraConfig, objectMapper: ObjectMapper) {
  
  private val logger = LoggerFactory.getLogger(classOf[MsEntraClient])
  
  /**
   * Exchange authorization code for access token (OAuth2 token endpoint)
   * Called after user authenticates via MS Entra login page
   */
  def exchangeCodeForToken(authorizationCode: String): MsEntraTokenResponse = {
    val tokenUrl = s"https://login.microsoftonline.com/${config.tenantId}/oauth2/v2.0/token"
    
    val params = Map(
      "client_id" -> config.clientId,
      "client_secret" -> config.clientSecret,
      "code" -> authorizationCode,
      "redirect_uri" -> config.redirectUri,
      "grant_type" -> "authorization_code",
      "scope" -> config.scope
    )
    
    val response = postRequest(tokenUrl, params)
    parseTokenResponse(response)
  }
  
  /**
   * Refresh access token using refresh token
   */
  def refreshAccessToken(refreshToken: String): MsEntraTokenResponse = {
    val tokenUrl = s"https://login.microsoftonline.com/${config.tenantId}/oauth2/v2.0/token"
    
    val params = Map(
      "client_id" -> config.clientId,
      "client_secret" -> config.clientSecret,
      "refresh_token" -> refreshToken,
      "grant_type" -> "refresh_token",
      "scope" -> config.scope
    )
    
    postRequest(tokenUrl, params).map(parseTokenResponse).get
  }
  
  /**
   * Call Microsoft Graph API to get user profile information
   */
  def getUserInfo(accessToken: String): MsEntraUserInfo = {
    val graphUrl = "https://graph.microsoft.com/v1.0/me"
    
    val response = getRequest(graphUrl, accessToken)
    parseUserInfoResponse(response)
  }
  
  private def postRequest(url: String, params: Map[String, String]): Try[String] = Try {
    val connection = new java.net.URL(url).openConnection().asInstanceOf[java.net.HttpURLConnection]
    connection.setRequestMethod("POST")
    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
    connection.setDoOutput(true)
    
    val body = params.map { case (k, v) => s"$k=${java.net.URLEncoder.encode(v, "UTF-8")}" }
      .mkString("&")
    
    connection.getOutputStream.write(body.getBytes("UTF-8"))
    
    Using(Source.fromInputStream(connection.getInputStream))(_.mkString).get
  }
  
  private def getRequest(url: String, accessToken: String): Try[String] = Try {
    val connection = new java.net.URL(url).openConnection().asInstanceOf[java.net.HttpURLConnection]
    connection.setRequestMethod("GET")
    connection.setRequestProperty("Authorization", s"Bearer $accessToken")
    
    Using(Source.fromInputStream(connection.getInputStream))(_.mkString).get
  }
  
  private def parseTokenResponse(jsonStr: String): MsEntraTokenResponse = {
    val json: JsonNode = objectMapper.readTree(jsonStr)
    MsEntraTokenResponse(
      accessToken = json.get("access_token").asText(),
      refreshToken = Option(json.get("refresh_token")).map(_.asText()),
      expiresIn = json.get("expires_in").asInt(),
      idToken = Option(json.get("id_token")).map(_.asText())
    )
  }
  
  private def parseUserInfoResponse(jsonStr: String): MsEntraUserInfo = {
    val json: JsonNode = objectMapper.readTree(jsonStr)
    MsEntraUserInfo(
      userId = json.get("id").asText(),
      userPrincipalName = json.get("userPrincipalName").asText(),
      displayName = Option(json.get("displayName")).map(_.asText()),
      mail = Option(json.get("mail")).map(_.asText()),
      jobTitle = Option(json.get("jobTitle")).map(_.asText())
    )
  }
}

case class MsEntraTokenResponse(
  accessToken: String,
  refreshToken: Option[String],
  expiresIn: Int,
  idToken: Option[String]
)

case class MsEntraUserInfo(
  userId: String,
  userPrincipalName: String,
  displayName: Option[String],
  mail: Option[String],
  jobTitle: Option[String]
)
```

---

### Phase 3: Authentication Provider

**New File**: `api/src/main/scala/za/co/absa/loginsvc/rest/provider/entra/MsEntraAuthenticationProvider.scala`

```scala
package za.co.absa.loginsvc.rest.provider.entra

import com.fasterxml.jackson.databind.ObjectMapper
import io.jsonwebtoken.Jwts
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.{AuthenticationProvider, BadCredentialsException}
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import za.co.absa.loginsvc.model.User
import za.co.absa.loginsvc.rest.config.auth.MsEntraConfig

import scala.collection.JavaConverters._
import scala.util.Try

/**
 * Authenticates users via MS Entra (Azure AD) token exchange.
 * 
 * Expected flow:
 * 1. Client obtains authorization code from MS Entra login page
 * 2. Client sends code to /token/generate via special header or param
 * 3. This provider exchanges code for access token
 * 4. Provider fetches user info from Microsoft Graph API
 * 5. Provider creates User object with groups from Graph
 * 6. JWTService then generates login-service JWT tokens
 */
class MsEntraAuthenticationProvider(
  config: MsEntraConfig,
  objectMapper: ObjectMapper
) extends AuthenticationProvider {

  private val logger = LoggerFactory.getLogger(classOf[MsEntraAuthenticationProvider])
  private val entraClient = new MsEntraClient(config, objectMapper)

  override def authenticate(authentication: Authentication): Authentication = {
    val entraAuth = authentication.asInstanceOf[MsEntraAuthenticationToken]
    val authorizationCode = entraAuth.getCredentials.toString

    logger.info(s"Authenticating via MS Entra with authorization code")

    try {
      // Step 1: Exchange authorization code for access token
      val tokenResponse = entraClient.exchangeCodeForToken(authorizationCode)
      logger.debug(s"Received access token from MS Entra")

      // Step 2: Parse ID token (optional) to get basic user info
      val idTokenClaims = tokenResponse.idToken.flatMap { token =>
        Try {
          val claims = Jwts.parserBuilder()
            .setSigningKey("") // ID tokens are typically validated against published keys
            .build()
            .parseClaimsJws(token)
            .getBody
          Option(claims)
        }.toOption.flatten
      }

      // Step 3: Fetch detailed user info from Microsoft Graph
      val graphUserInfo = entraClient.getUserInfo(tokenResponse.accessToken)
      logger.info(s"Retrieved user info for ${graphUserInfo.userPrincipalName}")

      // Step 4: Fetch user's group memberships from Microsoft Graph
      val userGroups = fetchUserGroups(tokenResponse.accessToken, graphUserInfo.userId)
      logger.debug(s"User ${graphUserInfo.userPrincipalName} has groups: $userGroups")

      // Step 5: Build User object
      val userAttributes = Map(
        "mail" -> graphUserInfo.mail,
        "displayname" -> graphUserInfo.displayName,
        "jobTitle" -> graphUserInfo.jobTitle
      ).collect { case (k, Some(v)) => k -> Some(v) }

      val principal = User(
        name = graphUserInfo.userPrincipalName,
        groups = userGroups,
        optionalAttributes = userAttributes
      )

      // Step 6: Return successful authentication
      val token = new MsEntraAuthenticationToken(principal, authorizationCode)
      token.setAuthenticated(true)
      token.setDetails(Map(
        "accessToken" -> tokenResponse.accessToken,
        "refreshToken" -> tokenResponse.refreshToken.getOrElse(""),
        "expiresIn" -> tokenResponse.expiresIn
      ).asJava)
      token
      
    } catch {
      case e: Throwable =>
        logger.error(s"MS Entra authentication failed: ${e.getMessage}", e)
        throw new BadCredentialsException("MS Entra authentication failed", e)
    }
  }

  override def supports(authentication: Class[_]): Boolean =
    authentication == classOf[MsEntraAuthenticationToken]

  /**
   * Fetch user's group memberships from Microsoft Graph
   * Requires User.Read and Group.Read.All permissions
   */
  private def fetchUserGroups(accessToken: String, userId: String): Seq[String] = {
    try {
      // Call: GET https://graph.microsoft.com/v1.0/me/memberOf
      // Returns: List of group IDs, names, etc.
      
      // Placeholder: Actual implementation would use MsEntraClient or direct HTTP call
      Seq("entra-user")  // Minimum group
    } catch {
      case e: Throwable =>
        logger.warn(s"Failed to fetch user groups from MS Entra: ${e.getMessage}", e)
        Seq.empty[String]
    }
  }
}

/**
 * Custom Authentication token for MS Entra
 * Stores authorization code and later the access token
 */
class MsEntraAuthenticationToken(
  principal: Any,
  credentials: Any
) extends org.springframework.security.core.AbstractAuthenticationToken(
  java.util.Collections.emptyList()
) {
  setAuthenticated(false)
  setPrincipal(principal)
  setCredentials(credentials)

  override def getCredentials: AnyRef = credentials.asInstanceOf[AnyRef]
  override def getPrincipal: AnyRef = principal.asInstanceOf[AnyRef]
}
```

---

### Phase 4: New REST Endpoints

**File**: Modify `/Users/ab006hm/Projects/login-service/api/src/main/scala/za/co/absa/loginsvc/rest/controller/TokenController.scala`

Add new endpoint for MS Entra token exchange:

```scala
@PostMapping(
  path = Array("/generate-entra"),
  produces = Array(MediaType.APPLICATION_JSON_VALUE)
)
@ResponseStatus(HttpStatus.OK)
@Operation(
  summary = "Generate tokens via MS Entra (Azure AD) token exchange",
  description = "Exchanges MS Entra authorization code for login-service JWT tokens"
)
def generateTokenEntra(
  @RequestHeader("X-Entra-Code") entraAuthCode: String,
  @RequestParam("group-prefixes") groupPrefixes: Optional[String],
  @RequestParam(name = "case-sensitive", defaultValue = "false") caseSensitive: Boolean
): TokensWrapper = {
  
  // Create MS Entra authentication token from authorization code
  val entraToken = new MsEntraAuthenticationToken(null, entraAuthCode)
  
  // Let AuthenticationManager handle it (will use MsEntraAuthenticationProvider)
  val authentication = authManager.authenticate(entraToken)
  
  val user: User = authentication.getPrincipal match {
    case u: User => u
    case _ => throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Failed to extract user from MS Entra")
  }
  
  // Apply group filtering if requested
  val filteredGroupsUser = user.applyIfDefined(groupPrefixes.toScalaOption) { (u: User, prefixesStr: String) =>
    val prefixes = prefixesStr.trim.split(',')
    u.filterGroupsByPrefixes(prefixes.toSet, caseSensitive)
  }
  
  // Generate login-service tokens
  val accessJwt = jwtService.generateAccessToken(filteredGroupsUser)
  val refreshJwt = jwtService.generateRefreshToken(filteredGroupsUser)
  TokensWrapper.fromTokens(accessJwt, refreshJwt)
}
```

---

### Phase 5: Configuration Update

**Update YAML Config** (example.application.yaml):

```yaml
loginsvc:
  rest:
    auth:
      provider:
        users:
          order: 1
          known-users: [...]
        
        ldap:
          order: 2
          # ... existing LDAP config
        
        ms-entra:                    # NEW
          order: 3
          tenantId: "${ENTRA_TENANT_ID}"  # From environment or secrets
          clientId: "${ENTRA_CLIENT_ID}"
          clientSecret: "${ENTRA_CLIENT_SECRET}"
          redirectUri: "https://myservice/callback"
          # OR use AWS Secrets:
          # awsSecretsManagerConfig:
          #   secretName: "entra-credentials"
          #   region: "us-east-1"
          #   clientSecretFieldName: "client-secret"
          
          scope: "https://graph.microsoft.com/.default"
          attributes:
            mail: "email"
            displayName: "display_name"
            jobTitle: "job_title"
```

---

### Phase 6: Integration with AuthManagerConfig

**Update**: `api/src/main/scala/za/co/absa/loginsvc/rest/AuthManagerConfig.scala`

```scala
private def createAuthProviders(configs: Array[ConfigOrdering]): Array[AuthenticationProvider] = {
  Array.empty[AuthenticationProvider] ++ configs.filter(_.order > 0).sortBy(_.order)
    .map {
      case c: UsersConfig => new ConfigUsersAuthenticationProvider(c)
      case c: ActiveDirectoryLDAPConfig => new ActiveDirectoryLDAPAuthenticationProvider(c)
      case c: MsEntraConfig => new MsEntraAuthenticationProvider(c, objectMapper)  // NEW
      case other => throw new IllegalStateException(s"unsupported config $other")
    }
}
```

---

### Phase 7: Update ConfigProvider

**Update**: `api/src/main/scala/za/co/absa/loginsvc/rest/config/provider/ConfigProvider.scala`

```scala
def getMsEntraConfig: Option[MsEntraConfig] = {
  val msEntraConfigOption = createConfigClass[MsEntraConfig]("loginsvc.rest.auth.provider.ms-entra")
  if (msEntraConfigOption.nonEmpty)
    msEntraConfigOption.get.throwErrors()
  msEntraConfigOption
}

// Add to AuthManagerConfig initialization
```

---

## TESTING STRATEGY

### Unit Tests

**New File**: `api/src/test/scala/za/co/absa/loginsvc/rest/provider/entra/MsEntraAuthenticationProviderTest.scala`

```scala
class MsEntraAuthenticationProviderTest extends AnyFlatSpec with Matchers {
  
  it should "exchange authorization code for access token" in {
    // Mock MsEntraClient
    val mockClient = mock[MsEntraClient]
    when(mockClient.exchangeCodeForToken("test-code")).thenReturn(
      MsEntraTokenResponse("access-token-123", Some("refresh-token"), 3600, None)
    )
    
    // Test token exchange
  }
  
  it should "create User object with MS Entra user info" in {
    // Verify user is created with correct fields from Graph API
  }
  
  it should "handle MS Entra errors gracefully" in {
    // Test BadCredentialsException on auth failure
  }
}
```

### Integration Tests

```scala
@WebMvcTest(controllers = Array(classOf[TokenController]))
class MsEntraTokenControllerTest extends AnyFlatSpec {
  
  it should "generate tokens via MS Entra code exchange" in {
    mockMvc.perform(
      post("/token/generate-entra")
        .header("X-Entra-Code", "auth-code-123")
        .contentType(MediaType.APPLICATION_JSON)
    )
    .andExpect(status.isOk)
    .andExpect(jsonPath("$.token").exists())
    .andExpect(jsonPath("$.refresh").exists())
  }
}
```

---

## SECURITY CONSIDERATIONS

1. **Client Secret Management**:
   - Never hardcode in YAML (use environment variables or AWS Secrets)
   - Rotate regularly
   - Use AWS Secrets Manager with version staging

2. **Authorization Code**:
   - Must be exchanged immediately (short-lived, ~10 minutes)
   - Should include PKCE (Proof Key for Code Exchange) for public clients
   - Prevent authorization code interception via HTTPS only

3. **Token Validation**:
   - Validate ID token signature against Azure's published keys
   - Validate `aud` (audience) claim matches `clientId`
   - Check `iss` (issuer) matches tenant

4. **Refresh Tokens**:
   - Store securely (not in localStorage on client side)
   - Use refresh token rotation if supported
   - Include `refresh_token_expires_in` for lifecycle management

5. **Scopes**:
   - Request minimum necessary scopes (principle of least privilege)
   - `https://graph.microsoft.com/.default` for all permissions
   - Could restrict to `User.Read` if only basic info needed

6. **HTTPS Only**:
   - All Entra communication must use TLS
   - Redirect URI must use HTTPS in production

---

## MIGRATION PATH

### Backward Compatibility
- All existing auth methods (config users, LDAP, Kerberos) continue to work unchanged
- New MS Entra provider added as **optional 4th provider**
- No breaking changes to existing endpoints

### Gradual Rollout
1. Deploy with MS Entra disabled (`order: 0` or omitted)
2. Test with small user subset
3. Enable for specific groups/teams
4. Full rollout when ready

---

## DEPENDENCIES TO ADD

**Update** `project/Dependencies.scala`:

```scala
// Microsoft Graph & OAuth2
lazy val microsoftGraphJavaSDK = "com.microsoft.graph" % "microsoft-graph" % "5.35.0"
lazy val azureIdentity = "com.azure" % "azure-identity" % "1.8.2"
lazy val joseJwt = "com.nimbusds" % "nimbus-jose-jwt" % "9.31"  // Already included

// Or use lightweight HTTP client approach (already have jackson)
```

---

## KEY DIFFERENCES FROM LDAP INTEGRATION

| Aspect | LDAP | MS Entra |
|--------|------|----------|
| **Protocol** | LDAP/LDAPS | OAuth2 + REST API |
| **Group Fetch** | LDAP query + service account | MS Graph API + access token |
| **Discovery** | Manual URL/domain config | Azure metadata discovery |
| **Token Exchange** | N/A (direct LDAP bind) | Code → Access Token → User Info |
| **Refresh** | No token refresh | Token refresh via refresh token |
| **Attributes** | LDAP attributes directly | MS Graph API fields |

---

## EXAMPLE CLIENT USAGE

```javascript
// 1. Redirect user to MS Entra login
const entraLoginUrl = `https://login.microsoftonline.com/${tenantId}/oauth2/v2.0/authorize?client_id=${clientId}&redirect_uri=${redirectUri}&response_type=code&scope=openid%20profile%20email`;

// 2. User logs in, gets authorization code
// Azure redirects to: https://myapp/callback?code=ABC123&session_state=XYZ

// 3. Exchange code for login-service tokens
const response = await fetch('/token/generate-entra', {
  method: 'POST',
  headers: {
    'X-Entra-Code': 'ABC123',
    'Content-Type': 'application/json'
  }
});

const { token, refresh } = await response.json();

// 4. Use token for API requests
fetch('/api/protected', {
  headers: {
    'Authorization': `Bearer ${token}`
  }
});
```

