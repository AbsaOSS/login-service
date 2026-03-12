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

import java.security.KeyPairGenerator
import java.util.{Date, UUID}
import scala.collection.JavaConverters._
import scala.util.Success

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
}
