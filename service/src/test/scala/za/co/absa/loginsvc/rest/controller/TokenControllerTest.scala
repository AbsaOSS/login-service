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

package za.co.absa.loginsvc.rest.controller

import com.nimbusds.jose.jwk.{JWKSet, RSAKey}
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.flatspec.AnyFlatSpec
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import za.co.absa.loginsvc.model.User
import za.co.absa.loginsvc.rest.{FakeAuthentication, SecurityConfig}
import za.co.absa.loginsvc.rest.service.JWTService

import java.security.interfaces.RSAPublicKey
import java.util.Base64

@Import(Array(classOf[SecurityConfig]))
@WebMvcTest(controllers = Array(classOf[TokenController]))
class TokenControllerTest extends AnyFlatSpec with ControllerIntegrationTestBase {
  import AssertionsForEndpointWithCompletableFuture._

  @Autowired
  override var mockMvc: MockMvc = _

  @MockBean
  private var jwtService: JWTService = _


  behavior of "generateToken"

  val fakeJWT = "abc.fakeJWTToken.abc"

  it should "return token generated by mocked JWTService for the authenticated user" in {
    when(jwtService.generateAccessToken(FakeAuthentication.fakeUser)).thenReturn(fakeJWT)

    assertOkAndResultBodyJsonEquals(
      "/token/generate",
      Post(),
      s"""{"token": "$fakeJWT"}"""
    )(FakeAuthentication.fakeUserAuthentication)
  }

  it should "return token generated by mocked JWTService for the authenticated user with group-prefixes (single)" in {
    // `groups-prefixes` fill change the groups in user object passed to the jwtService.generateToken
    val fakeUserFilteredGroups = FakeAuthentication.fakeUser.copy(groups = Seq("first-fake-group"))
    when(jwtService.generateAccessToken(fakeUserFilteredGroups)).thenReturn(fakeJWT)

    assertOkAndResultBodyJsonEquals(
      "/token/generate?group-prefixes=first",
      Post(),
      s"""{"token": "$fakeJWT"}"""
    )(FakeAuthentication.fakeUserAuthentication)
  }

  it should "return token generated by mocked JWTService for the authenticated user with group-prefixes (multiple ,-separated)" in {
    val fakeUserFilteredGroups = FakeAuthentication.fakeUser.copy(groups = Seq("second-fake-group", "third-fake-group"))
    when(jwtService.generateAccessToken(fakeUserFilteredGroups)).thenReturn(fakeJWT)

    assertOkAndResultBodyJsonEquals(
      "/token/generate?group-prefixes=second,third,nonexistent",
      Post(),
      s"""{"token": "$fakeJWT"}"""
    )(FakeAuthentication.fakeUserAuthentication)
  }

  it should "fail for anonymous (not authenticated) user" in {
    when(jwtService.generateAccessToken(any[User]())).thenReturn(fakeJWT)

    assertNotAuthenticatedFailure(
      "/token/generate",
      Post()
    )(FakeAuthentication.fakeAnonymousAuthentication)
  }

  behavior of "getPublicKey"

  it should "return a Base64 encoded public key from JWTService when user is authenticated" in {
    val publicKey = Keys.keyPairFor(SignatureAlgorithm.RS256).getPublic
    when(jwtService.publicKey).thenReturn(publicKey)

    val expectedPublicKeyBase64 = Base64.getEncoder.encodeToString(publicKey.getEncoded)

    assertOkAndResultBodyJsonEquals(
      "/token/public-key",
      Get(),
      s"""{"key": "$expectedPublicKeyBase64"}"""
    )(FakeAuthentication.fakeUserAuthentication)
  }

  it should "return a Base64 encoded public key from JWTService when user is not authenticated" in {
    val publicKey = Keys.keyPairFor(SignatureAlgorithm.RS256).getPublic
    when(jwtService.publicKey).thenReturn(publicKey)

    val expectedPublicKeyBase64 = Base64.getEncoder.encodeToString(publicKey.getEncoded)

    assertOkAndResultBodyJsonEquals(
      "/token/public-key",
      Get(),
      s"""{"key": "$expectedPublicKeyBase64"}"""
    )(FakeAuthentication.fakeAnonymousAuthentication)
  }

  behavior of "getPublicKeyJwks"

  it should "return a JWKS from JWTService when user is authenticated" in {
    val publicKey = Keys.keyPairFor(SignatureAlgorithm.RS256).getPublic
    val jwk = new RSAKey.Builder(publicKey.asInstanceOf[RSAPublicKey]).build()
    val jwks = new JWKSet(jwk)

    when(jwtService.jwks).thenReturn(jwks)

    val expectedResponse = s"""
         |{
         |  "keys": [
         |    {
         |      "kty":"${jwk.getKeyType}",
         |      "e":"${jwk.getPublicExponent}",
         |      "n":"${jwk.getModulus}"
         |    }
         |  ]
         |}
         |""".stripMargin

    assertOkAndResultBodyJsonEquals(
      "/token/public-key-jwks",
      Get(),
      expectedResponse
    )(FakeAuthentication.fakeUserAuthentication)
  }

  it should "return a JWKS from JWTService when user is not authenticated" in {
    val publicKey = Keys.keyPairFor(SignatureAlgorithm.RS256).getPublic
    val jwk = new RSAKey.Builder(publicKey.asInstanceOf[RSAPublicKey]).build()
    val jwks = new JWKSet(jwk)

    when(jwtService.jwks).thenReturn(jwks)

    val expectedResponse =
      s"""
         |{
         |  "keys": [
         |    {
         |      "kty":"${jwk.getKeyType}",
         |      "e":"${jwk.getPublicExponent}",
         |      "n":"${jwk.getModulus}"
         |    }
         |  ]
         |}
         |""".stripMargin

    assertOkAndResultBodyJsonEquals(
      "/token/public-key-jwks",
      Get(),
      expectedResponse
    )(FakeAuthentication.fakeAnonymousAuthentication)
  }
}
