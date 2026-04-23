/*
 * Copyright 2023 ABSA Group Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package za.co.absa.loginsvc.rest.provider.entra

import com.google.common.cache.{CacheBuilder, CacheLoader, LoadingCache}
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.source.{JWKSource, RemoteJWKSet}
import com.nimbusds.jose.proc.{JWSVerificationKeySelector, SecurityContext => NimbusSecurityContext}
import com.nimbusds.jose.util.DefaultResourceRetriever
import com.nimbusds.jwt.proc.{BadJWTException, DefaultJWTClaimsVerifier, DefaultJWTProcessor}
import com.nimbusds.jwt.{JWTClaimsSet, SignedJWT}
import org.slf4j.LoggerFactory
import za.co.absa.loginsvc.model.User
import za.co.absa.loginsvc.rest.config.auth.MsEntraConfig

import java.net.URL
import java.util.concurrent.TimeUnit
import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

/**
 * Validates MS Entra (Azure AD) Bearer JWT tokens.
 *
 * Fetches the JWKS URI from Microsoft's OIDC discovery endpoint and caches it.
 * Validates the token's signature, expiry, issuer and audience, then extracts a [[User]].
 *
 * The discovery URL follows the Microsoft standard:
 *   https://login.microsoftonline.com/{tenantId}/v2.0/.well-known/openid-configuration
 *
 * @param config              Entra configuration
 * @param jwkSourceOverride   Optional override for the JWK source (used in tests to avoid HTTP calls)
 * @param graphClientOverride Optional override for the Graph username resolver (used in tests)
 */
class MsEntraTokenValidator(
  config: MsEntraConfig,
  private[entra] val jwkSourceOverride: Option[JWKSource[NimbusSecurityContext]] = None,
  private[entra] val graphClientOverride: Option[GraphUsernameResolver] = None
) {

  private val logger = LoggerFactory.getLogger(classOf[MsEntraTokenValidator])

  // Use the injected graph resolver (for tests) or create one from config if clientSecret is set
  private val graphResolver: Option[GraphUsernameResolver] =
    graphClientOverride.orElse(config.clientSecret.map(_ => new MsEntraGraphClient(config)))

  private val discoveryUrl =
    s"${config.loginBaseUrl}/${config.tenantId}/v2.0/.well-known/openid-configuration"

  private val expectedIssuer =
    s"${config.loginBaseUrl}/${config.tenantId}/v2.0"

  // Cache the JWKSource keyed by jwks_uri string; refreshes after 1 hour
  private val jwkSourceCache: LoadingCache[String, JWKSource[NimbusSecurityContext]] =
    CacheBuilder.newBuilder()
      .expireAfterWrite(1, TimeUnit.HOURS)
      .build(new CacheLoader[String, JWKSource[NimbusSecurityContext]] {
        override def load(jwksUri: String): JWKSource[NimbusSecurityContext] = {
          logger.info(s"Loading JWKS from $jwksUri (connectTimeoutMs=${config.jwksConnectTimeoutMs}, readTimeoutMs=${config.jwksReadTimeoutMs})")
          new RemoteJWKSet[NimbusSecurityContext](
            new URL(jwksUri),
            new DefaultResourceRetriever(config.jwksConnectTimeoutMs, config.jwksReadTimeoutMs)
          )
        }
      })

  /**
   * Validates the given raw Entra JWT string.
   *
   * @param rawToken the Bearer token string (without "Bearer " prefix)
   * @return a [[User]] if the token is valid, or a Failure with a descriptive exception
   */
  def validate(rawToken: String): Try[User] = {
    Try {
      val jwkSource = jwkSourceOverride.getOrElse {
        val jwksUri = resolveJwksUri()
        jwkSourceCache.get(jwksUri)
      }

      val jwtProcessor = new DefaultJWTProcessor[NimbusSecurityContext]()
      val keySelector = new JWSVerificationKeySelector[NimbusSecurityContext](
        JWSAlgorithm.RS256,
        jwkSource
      )
      jwtProcessor.setJWSKeySelector(keySelector)

      // Verify signature, issuer, expiry and required claims
      val requiredClaims = new DefaultJWTClaimsVerifier[NimbusSecurityContext](
        new JWTClaimsSet.Builder().issuer(expectedIssuer).build(),
        Set("sub", "iat", "exp").asJava
      )
      jwtProcessor.setJWTClaimsSetVerifier(requiredClaims)

      val claims: JWTClaimsSet = jwtProcessor.process(rawToken, null)

      // Audience check: if audiences are configured, token must contain at least one
      if (config.audiences.nonEmpty) {
        val tokenAudiences = Option(claims.getAudience).map(_.asScala.toSet).getOrElse(Set.empty)
        val configAudiences = config.audiences.toSet
        if (tokenAudiences.intersect(configAudiences).isEmpty)
          throw new BadJWTException(
            s"JWT aud claim has value $tokenAudiences, must include one of $configAudiences"
          )
      }

      extractUser(claims)
    } recoverWith {
      case e: BadJWTException =>
        logger.warn(s"Entra token validation failed (claims): ${e.getMessage}")
        Failure(e)
      case e: Exception =>
        logger.warn(s"Entra token validation failed: ${e.getMessage}")
        Failure(e)
    }
  }

  private def extractUser(claims: JWTClaimsSet): User = {
    val rawUsername = Option(claims.getStringClaim("preferred_username"))
      .orElse(Option(claims.getStringClaim("upn")))
      .orElse(Option(claims.getSubject))
      .getOrElse(throw new IllegalArgumentException("Entra token has no usable username claim (preferred_username/upn/sub)"))

    // Attempt to resolve to lower-case on-premises samAccountName via Graph API; fall back to UPN
    val username = graphResolver.flatMap(_.resolveUsername(rawUsername)).getOrElse(rawUsername)

    val groups: Seq[String] = Option(claims.getStringListClaim("groups"))
      .map(_.asScala.toSeq)
      .getOrElse(Seq.empty)

    val optionalAttributes: Map[String, Option[AnyRef]] = config.attributes.getOrElse(Map.empty).flatMap {
      case (claimName, lsClaimName) =>
        Option(claims.getClaim(claimName)).map { value =>
          lsClaimName -> Some(value.asInstanceOf[AnyRef])
        }
    }

    User(username, groups, optionalAttributes)
  }

  private def resolveJwksUri(): String = {
    val conn = new URL(discoveryUrl).openConnection()
    conn.setConnectTimeout(5000)
    conn.setReadTimeout(5000)
    val json = scala.io.Source.fromInputStream(conn.getInputStream).mkString
    // Simple string extraction without pulling in additional JSON libraries
    val jwksUriPattern = """"jwks_uri"\s*:\s*"([^"]+)"""".r
    jwksUriPattern.findFirstMatchIn(json)
      .map(_.group(1))
      .getOrElse(throw new IllegalStateException(s"Could not find jwks_uri in OIDC discovery doc at $discoveryUrl"))
  }
}

object MsEntraTokenValidator {
  def apply(config: MsEntraConfig): MsEntraTokenValidator = new MsEntraTokenValidator(config)
}
