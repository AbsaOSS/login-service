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

import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.source.ImmutableJWKSet
import com.nimbusds.jose.jwk.{JWKSet, RSAKey}
import com.nimbusds.jose.proc.{SecurityContext => NimbusSecurityContext}
import com.nimbusds.jose.{JWSAlgorithm, JWSHeader}
import com.nimbusds.jwt.{JWTClaimsSet, SignedJWT}
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.{mock, when}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import za.co.absa.loginsvc.rest.config.auth.MsEntraConfig

import com.sun.net.httpserver.{HttpExchange, HttpServer}

import java.net.InetSocketAddress
import java.security.KeyPairGenerator
import java.util.{Date, UUID}
import scala.collection.JavaConverters._
import scala.util.{Failure, Success}

class MsEntraTokenValidatorTest extends AnyFlatSpec with Matchers {

  private val tenantId = "test-tenant-id"
  private val clientId = "test-client-id"
  private val audience = "api://test-client-id"
  private val audience2 = "other-app-client-id"
  private val issuer = s"https://login.microsoftonline.com/$tenantId/v2.0"

  private val config = MsEntraConfig(
    tenantId = tenantId,
    clientId = clientId,
    audiences = List(audience, audience2),
    order = 1,
    attributes = Some(Map("email" -> "email"))
  )

  // Generate a real RSA key pair for testing
  private val keyPairGenerator = KeyPairGenerator.getInstance("RSA")
  keyPairGenerator.initialize(2048)
  private val keyPair = keyPairGenerator.generateKeyPair()
  private val rsaJwk = new RSAKey.Builder(keyPair.getPublic.asInstanceOf[java.security.interfaces.RSAPublicKey])
    .privateKey(keyPair.getPrivate)
    .keyID(UUID.randomUUID().toString)
    .build()

  private val jwkSet = new JWKSet(rsaJwk)
  private val jwkSource = new ImmutableJWKSet[NimbusSecurityContext](jwkSet)

  private val validator = new MsEntraTokenValidator(config, Some(jwkSource))

  private def buildToken(
    subject: String = "user-oid-123",
    preferredUsername: String = "user@example.com",
    groups: Seq[String] = Seq("group1", "group2"),
    email: String = "user@example.com",
    expiresInSeconds: Int = 3600,
    issuerOverride: String = issuer,
    audienceOverride: String = audience
  ): String = {
    val now = new Date()
    val exp = new Date(now.getTime + expiresInSeconds * 1000L)

    val claims = new JWTClaimsSet.Builder()
      .subject(subject)
      .issuer(issuerOverride)
      .audience(audienceOverride)
      .issueTime(now)
      .expirationTime(exp)
      .claim("preferred_username", preferredUsername)
      .claim("groups", groups.asJava)
      .claim("email", email)
      .build()

    val header = new JWSHeader.Builder(JWSAlgorithm.RS256)
      .keyID(rsaJwk.getKeyID)
      .build()

    val jwt = new SignedJWT(header, claims)
    jwt.sign(new RSASSASigner(rsaJwk))
    jwt.serialize()
  }

  "MsEntraTokenValidator" should "return a User for a valid token" in {
    val token = buildToken()
    val result = validator.validate(token)

    result shouldBe a[Success[_]]
    val user = result.get
    user.name shouldBe "user@example.com"
    user.groups should contain theSameElementsAs Seq("group1", "group2")
  }

  it should "map configured attribute claims to optional attributes" in {
    val token = buildToken(email = "mapped@example.com")
    val user = validator.validate(token).get
    user.optionalAttributes.get("email") shouldBe Some(Some("mapped@example.com"))
  }

  it should "use 'upn' claim as username when preferred_username is absent" in {
    val now = new Date()
    val exp = new Date(now.getTime + 3600 * 1000L)
    val claims = new JWTClaimsSet.Builder()
      .subject("sub-id")
      .issuer(issuer)
      .audience(audience)
      .issueTime(now)
      .expirationTime(exp)
      .claim("upn", "upnuser@example.com")
      .claim("groups", Seq.empty[String].asJava)
      .build()
    val jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaJwk.getKeyID).build(), claims)
    jwt.sign(new RSASSASigner(rsaJwk))

    val user = validator.validate(jwt.serialize()).get
    user.name shouldBe "upnuser@example.com"
  }

  it should "fall back to sub claim as username when neither preferred_username nor upn is present" in {
    val now = new Date()
    val exp = new Date(now.getTime + 3600 * 1000L)
    val claims = new JWTClaimsSet.Builder()
      .subject("sub-only-user")
      .issuer(issuer)
      .audience(audience)
      .issueTime(now)
      .expirationTime(exp)
      .claim("groups", Seq.empty[String].asJava)
      .build()
    val jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaJwk.getKeyID).build(), claims)
    jwt.sign(new RSASSASigner(rsaJwk))

    val user = validator.validate(jwt.serialize()).get
    user.name shouldBe "sub-only-user"
  }

  it should "return a Failure for an expired token" in {
    // Use -120s to exceed Nimbus's default 60s clock-skew tolerance
    val token = buildToken(expiresInSeconds = -120)
    val result = validator.validate(token)
    result.isFailure shouldBe true
  }

  it should "return a Failure for a token with wrong issuer" in {
    val token = buildToken(issuerOverride = "https://evil.example.com")
    val result = validator.validate(token)
    result.isFailure shouldBe true
  }

  it should "accept a token with the second configured audience" in {
    val token = buildToken(audienceOverride = audience2)
    validator.validate(token) shouldBe a[Success[_]]
  }

  it should "return a Failure for a token with wrong audience" in {
    val token = buildToken(audienceOverride = "api://different-client")
    val result = validator.validate(token)
    result.isFailure shouldBe true
  }

  it should "return a Failure for a malformed token string" in {
    val result = validator.validate("not.a.valid.jwt")
    result.isFailure shouldBe true
  }

  it should "return empty groups when groups claim is absent" in {
    val now = new Date()
    val exp = new Date(now.getTime + 3600 * 1000L)
    val claims = new JWTClaimsSet.Builder()
      .subject("sub-id")
      .issuer(issuer)
      .audience(audience)
      .issueTime(now)
      .expirationTime(exp)
      .claim("preferred_username", "nogroups@example.com")
      .build()
    val jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaJwk.getKeyID).build(), claims)
    jwt.sign(new RSASSASigner(rsaJwk))

    val user = validator.validate(jwt.serialize()).get
    user.groups shouldBe empty
  }

  it should "use the normalized samAccountName from Graph when graphClientOverride resolves the username" in {
    val mockGraph = mock(classOf[GraphUsernameResolver])
    when(mockGraph.resolveUsername("user@example.com")).thenReturn(Some("jsmith"))

    val validatorWithGraph = new MsEntraTokenValidator(config, Some(jwkSource), Some(mockGraph))
    val token = buildToken()
    val user = validatorWithGraph.validate(token).get
    user.name shouldBe "jsmith"
  }

  it should "fall back to UPN when the graph resolver returns None" in {
    val mockGraph = mock(classOf[GraphUsernameResolver])
    when(mockGraph.resolveUsername(anyString())).thenReturn(None)

    val validatorWithGraph = new MsEntraTokenValidator(config, Some(jwkSource), Some(mockGraph))
    val token = buildToken(preferredUsername = "fallback@example.com")
    val user = validatorWithGraph.validate(token).get
    user.name shouldBe "fallback@example.com"
  }

  private val v1Issuer = s"https://sts.windows.net/$tenantId/"
  private val spConfig = config.copy(allowServicePrincipals = true)
  private val spValidator = new MsEntraTokenValidator(spConfig, Some(jwkSource))

  private def buildAppToken(
    issuerOverride: String = issuer,
    roles: Option[Seq[String]] = Some(Seq("Role.A", "Role.B")),
    appClaim: Option[(String, String)] = Some("azp" -> "client-guid"),
    oid: String = "sp-object-id",
    idtyp: Option[String] = Some("app")
  ): String = {
    val builder = new JWTClaimsSet.Builder()
      .subject(oid)
      .issuer(issuerOverride)
      .audience(audience)
      .issueTime(new Date())
      .expirationTime(new Date(System.currentTimeMillis() + 3600 * 1000L))
      .claim("oid", oid)
    idtyp.foreach(builder.claim("idtyp", _))
    appClaim.foreach { case (name, value) => builder.claim(name, value) }
    roles.foreach(r => builder.claim("roles", r.asJava))
    val jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaJwk.getKeyID).build(), builder.build())
    jwt.sign(new RSASSASigner(rsaJwk))
    jwt.serialize()
  }

  it should "map an app-only token to a service principal user with roles as groups" in {
    val user = spValidator.validate(buildAppToken()).get
    user.name shouldBe "sp-client-guid"
    user.groups should contain theSameElementsAs Seq("Role.A", "Role.B")
    user.optionalAttributes.get("principalType") shouldBe Some(Some("service"))
  }

  it should "map an app-only token without roles to empty groups" in {
    spValidator.validate(buildAppToken(roles = None)).get.groups shouldBe empty
  }

  it should "use appid claim for v1 app-only tokens issued by sts.windows.net" in {
    val user = spValidator.validate(buildAppToken(issuerOverride = v1Issuer, appClaim = Some("appid" -> "v1-client"))).get
    user.name shouldBe "sp-v1-client"
  }

  it should "reject v1 issuer when service principals are not allowed" in {
    validator.validate(buildAppToken(issuerOverride = v1Issuer)) shouldBe a[Failure[_]]
  }

  it should "reject app-only tokens when service principals are not allowed" in {
    validator.validate(buildAppToken()) shouldBe a[Failure[_]]
  }

  it should "accept only allow-listed service principals when an allow list is configured" in {
    val listed = new MsEntraTokenValidator(spConfig.copy(servicePrincipalAllowList = Some(List("client-guid"))), Some(jwkSource))
    listed.validate(buildAppToken()) shouldBe a[Success[_]]
    listed.validate(buildAppToken(appClaim = Some("azp" -> "other"))) shouldBe a[Failure[_]]
  }

  it should "reject a config enabling service principals without audiences" in {
    val invalid = config.copy(allowServicePrincipals = true, audiences = List.empty)
    an[Exception] should be thrownBy invalid.throwErrors()
  }

  it should "detect an app-only token without idtyp when it has no user claims and oid equals sub" in {
    spValidator.validate(buildAppToken(idtyp = None)).get.name shouldBe "sp-client-guid"
  }

  it should "reject an app-only token without azp and appid claims" in {
    spValidator.validate(buildAppToken(appClaim = None)) shouldBe a[Failure[_]]
  }

  it should "accept a service principal by oid when listed in the allow list" in {
    val listed = new MsEntraTokenValidator(spConfig.copy(servicePrincipalAllowList = Some(List("sp-object-id"))), Some(jwkSource))
    listed.validate(buildAppToken()) shouldBe a[Success[_]]
  }

  it should "treat a token with user claims and no idtyp as a user token even when oid equals sub" in {
    val now = new Date()
    val claims = new JWTClaimsSet.Builder()
      .subject("same-id").issuer(issuer).audience(audience)
      .issueTime(now).expirationTime(new Date(now.getTime + 3600 * 1000L))
      .claim("oid", "same-id").claim("preferred_username", "person@example.com")
      .build()
    val jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaJwk.getKeyID).build(), claims)
    jwt.sign(new RSASSASigner(rsaJwk))

    spValidator.validate(jwt.serialize()).get.name shouldBe "person@example.com"
  }

  private def withDiscoveryServer(discoveryBody: String => String)(test: String => Unit): Unit = {
    val server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0)
    val baseUrl = s"http://127.0.0.1:${server.getAddress.getPort}"
    def respond(exchange: HttpExchange, body: String): Unit = {
      val bytes = body.getBytes("UTF-8")
      exchange.sendResponseHeaders(200, bytes.length)
      exchange.getResponseBody.write(bytes)
      exchange.close()
    }
    server.createContext(s"/$tenantId/v2.0/.well-known/openid-configuration", (e: HttpExchange) => respond(e, discoveryBody(baseUrl)))
    server.createContext("/keys", (e: HttpExchange) => respond(e, jwkSet.toString))
    server.start()
    try test(baseUrl) finally server.stop(0)
  }

  it should "resolve the JWKS from the OIDC discovery document when no JWK source is injected" in {
    withDiscoveryServer(base => s"""{"jwks_uri": "$base/keys"}""") { baseUrl =>
      val discoveringValidator = new MsEntraTokenValidator(config.copy(loginBaseUrl = baseUrl, allowServicePrincipals = true))
      val token = buildAppToken(issuerOverride = s"$baseUrl/$tenantId/v2.0")

      discoveringValidator.validate(token).get.name shouldBe "sp-client-guid"
    }
  }

  it should "fail when the OIDC discovery document has no jwks_uri" in {
    withDiscoveryServer(_ => "{}") { baseUrl =>
      val discoveringValidator = new MsEntraTokenValidator(config.copy(loginBaseUrl = baseUrl))

      discoveringValidator.validate(buildToken(issuerOverride = s"$baseUrl/$tenantId/v2.0")) shouldBe a[Failure[_]]
    }
  }
}
