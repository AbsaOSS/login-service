# Copilot Instructions — login-service

## Build & Test

```bash
# Compile everything (cross-builds clientLibrary for 2.12 + 2.13)
sbt +compile

# Run all tests
sbt +test

# Run tests for a single module
sbt "api / test"
sbt "clientLibrary / test"

# Run a single test class
sbt "api / testOnly za.co.absa.loginsvc.rest.controller.TokenControllerTest"

# Run a single test by name fragment
sbt "api / testOnly *TokenControllerTest -- -z \"return tokens\""

# Code coverage (JaCoCo) — runs clean + test + report across all modules
sbt jacoco
# Reports at: {module}/target/jacoco/report/index.html

# Start Tomcat locally (builds the WAR first)
sbt "api / Tomcat / start"
# Requires: --spring.config.location=api/src/main/resources/example.application.yaml
#   or env: SPRING_CONFIG_LOCATION=api/src/main/resources/example.application.yaml

# Generate cross-compiled docs
sbt +doc
```

CI runs: `sbt +test +doc` (see `.github/workflows/build.yml`).

## Architecture

### Module Layout

- **`api`** — The service itself. Spring Boot 2.7 app deployed as a WAR on Tomcat (via `xsbt-web-plugin`). Scala 2.12 only.
- **`clientLibrary`** — Standalone JWT verification library for consumers. Cross-compiled for Scala 2.12 and 2.13. Published to Maven.
- **`examples`** — Usage examples; depends on `clientLibrary`.

### Authentication Flow

The service issues RS256-signed JWTs. Clients authenticate via one of several pluggable providers, then receive access + refresh tokens.

```
Client request (Basic Auth / SPNEGO / Bearer)
  → Spring Security filter chain
    → AuthenticationManager (ProviderManager with ordered providers)
      → Provider authenticates, returns Authentication with User principal
  → TokenController.generateToken(authentication)
    → JWTService.generateAccessToken / generateRefreshToken
  → Response: { "token": "...", "refresh": "..." }
```

**Pluggable provider system:**
- Providers are enabled/disabled and ordered via the `order` field in YAML config (`0` = disabled, `1+` = active, lower = higher priority).
- `AuthManagerConfig` builds the `ProviderManager` from `AuthConfigProvider` results.
- `DefaultUserRepositories` builds a parallel ordered list of `UserRepository` implementations for user lookup during token refresh.
- When adding a new auth provider, you must wire it into: `AuthConfigProvider` trait, `ConfigProvider`, `AuthManagerConfig`, `SecurityConfig`, and `DefaultUserRepositories`.

**Existing providers:**
| Provider | Config Class | Auth Mechanism |
|---|---|---|
| Config Users | `UsersConfig` | Username/password from YAML |
| AD LDAP | `ActiveDirectoryLDAPConfig` | LDAP bind against Active Directory |
| Kerberos SPNEGO | `KerberosConfig` (nested in LDAP) | SPNEGO filter before BasicAuthFilter |

### Configuration System

All config is read from a single YAML file (path via `spring.config.location`) using **PureConfig** — not Spring's property binding.

- `ConfigProvider` reads the YAML with `YamlConfigSource` and exposes typed config via traits: `AuthConfigProvider`, `JwtConfigProvider`, `ExperimentalRestConfigProvider`.
- Config classes live under `config/auth/` and implement `ConfigValidatable` (custom validation) + `ConfigOrdering` (the `order: Int` trait).
- Validation uses `ConfigValidationResult` (a sealed trait with `Success`/`Error` variants that merge via `foldLeft`). Call `throwErrors()` to fail fast on startup.

### JWT Key Management

Two mutually exclusive key strategies (configured under `loginsvc.rest.jwt`):
- `generate-in-memory` — RSA key pair generated at startup, with optional scheduled rotation/layover/phase-out.
- `aws-secrets-manager` — Fetches RSA keys from AWS Secrets Manager with periodic polling.

`JWTService` handles generation, signing, refresh, and key rotation. It exposes keys as both raw `PublicKey` and `JWKSet`.

### Token Refresh

`JWTService.refreshTokens` validates both old access and refresh tokens, then calls `UserSearchService.searchUser(username)` to verify the user still exists and re-fetch their current groups. This means every auth provider should have a corresponding `UserRepository` implementation for refresh to work.

## Conventions

### New Auth Provider Checklist

1. Config case class in `config/auth/` — extend `ConfigValidatable` with `ConfigOrdering`
2. Add to `AuthConfigProvider` trait and implement in `ConfigProvider`
3. `AuthenticationProvider` impl in `provider/` — return `UsernamePasswordAuthenticationToken` with `User` principal
4. Register in `AuthManagerConfig.createAuthProviders` pattern match
5. `UserRepository` impl in `service/search/` for token refresh support
6. Register in `DefaultUserRepositories.createUserRepositories` pattern match
7. Wire filter (if non-Basic-Auth) in `SecurityConfig.filterChain`
8. Add example config block to `example.application.yaml`
9. Add test coverage following existing patterns

### File Headers

All source files require the Apache 2.0 license header (enforced by `sbt-header` plugin). The header is auto-managed — don't add it manually; run `sbt headerCreate` if needed.

### User Model

`User(name: String, groups: Seq[String], optionalAttributes: Map[String, Option[AnyRef]])` is the universal principal. All providers must produce a `User` instance. The `optionalAttributes` map carries extra claims (e.g., `email`, `displayname`) that get embedded in the access JWT.

### Test Patterns

- Unit tests use **ScalaTest** (`AnyFlatSpec` style) with **ScalaMock**.
- Controller integration tests extend `ControllerIntegrationTestBase` (bridges Spring's `TestContextManager` with ScalaTest lifecycle). Use `@WebMvcTest` + `MockMvc`.
- Test config: `api/src/test/resources/application.yaml` (uses in-memory keys, config-based users, LDAP disabled).
- Mock the `JWTService` in controller tests; test providers directly with their config classes.

### Scala Specifics

- Scala 2.12 for `api` module; use `scala.collection.JavaConverters._` (not `scala.jdk.CollectionConverters`).
- `clientLibrary` cross-compiles 2.12 + 2.13 — use `scala-collection-compat` there.
- Java 8 target bytecode (`-source 1.8 -target 1.8`) for backward compatibility.

### Spring / Swagger

- REST controllers use Spring MVC annotations. Swagger annotations from `springdoc-openapi-ui` (OpenAPI 3).
- `SecurityConfig` defines which paths are public (`permitAll`) vs authenticated. Update this when adding new public endpoints.
- Exception handling is centralized in `RestResponseEntityExceptionHandler` (`@ControllerAdvice`).
