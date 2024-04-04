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
import io.jsonwebtoken.security.Keys
import io.jsonwebtoken.{ExpiredJwtException, MalformedJwtException, SignatureAlgorithm}
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.flatspec.AnyFlatSpec
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Import
import org.springframework.test.web.servlet.MockMvc
import za.co.absa.loginsvc.model.User
import za.co.absa.loginsvc.rest.model.{AccessToken, RefreshToken}
import za.co.absa.loginsvc.rest.service.jwt.JWTService
import za.co.absa.loginsvc.rest.{FakeAuthentication, RestResponseEntityExceptionHandler, SecurityConfig}

import java.security.interfaces.RSAPublicKey
import java.util.Base64
import scala.concurrent.duration._

@Import(Array(classOf[SecurityConfig], classOf[RestResponseEntityExceptionHandler]))
@WebMvcTest(controllers = Array(classOf[TokenController]))
class TokenControllerTest extends AnyFlatSpec
  with ControllerIntegrationTestBase {
  import AssertionsForEndpointWithCompletableFuture._

  @Autowired
  override var mockMvc: MockMvc = _

  @MockBean
  private var jwtService: JWTService = _

  override def afterAll(): Unit = {
    super.afterAll()
    jwtService.close()
  }

  behavior of "generateToken"

  val fakeAccessJwt: AccessToken = AccessToken("abc.fakeJWTToken.abc")
  val fakeRefreshJwt: RefreshToken = RefreshToken("ab.fakeJWTToken.cd")
  val refreshDuration: FiniteDuration = 10.minutes

  it should "return tokens generated by mocked JWTService for the authenticated user" in {
    when(jwtService.generateAccessToken(FakeAuthentication.fakeUser)).thenReturn(fakeAccessJwt)
    when(jwtService.generateRefreshToken(FakeAuthentication.fakeUser)).thenReturn(fakeRefreshJwt)
    when(jwtService.getConfiguredRefreshExpDuration).thenReturn(refreshDuration)

    assertExpectedResponseFields(
      "/token/generate",
      Post()
    )(
      expectedJsonBody = s"""{"token": "${fakeAccessJwt.token}", "refresh": "${fakeRefreshJwt.token}"}"""
    )(Some(FakeAuthentication.fakeUserAuthentication))
  }

  it should "return tokens generated by mocked JWTService for the authenticated user with group-prefixes (single)" in {
    // `groups-prefixes` fill change the groups in user object passed to the jwtService.generateAccessToken
    val fakeUserFilteredGroups = FakeAuthentication.fakeUser.copy(groups = Seq("first-fake-group"))
    when(jwtService.generateAccessToken(fakeUserFilteredGroups)).thenReturn(fakeAccessJwt)
    when(jwtService.generateRefreshToken(fakeUserFilteredGroups)).thenReturn(fakeRefreshJwt)
    when(jwtService.getConfiguredRefreshExpDuration).thenReturn(refreshDuration)

    assertExpectedResponseFields(
      "/token/generate?group-prefixes=first",
      Post()
    )(
      expectedJsonBody = s"""{"token": "${fakeAccessJwt.token}", "refresh": "${fakeRefreshJwt.token}"}"""
    )(Some(FakeAuthentication.fakeUserAuthentication))
  }

  it should "return tokens generated by mocked JWTService for the authenticated user with group-prefixes (multiple ,-separated)" in {
    val fakeUserFilteredGroups = FakeAuthentication.fakeUser.copy(groups = Seq("second-fake-group", "third-fake-group"))
    when(jwtService.generateAccessToken(fakeUserFilteredGroups)).thenReturn(fakeAccessJwt)
    when(jwtService.generateRefreshToken(fakeUserFilteredGroups)).thenReturn(fakeRefreshJwt)
    when(jwtService.getConfiguredRefreshExpDuration).thenReturn(refreshDuration)

    assertExpectedResponseFields(
      "/token/generate?group-prefixes=second,third,nonexistent",
      Post()
    )(
      expectedJsonBody = s"""{"token": "${fakeAccessJwt.token}", "refresh": "${fakeRefreshJwt.token}"}"""
    )(Some(FakeAuthentication.fakeUserAuthentication))
  }

  it should "fail for anonymous (not authenticated) user" in {
    when(jwtService.generateAccessToken(any[User], any[Boolean])).thenReturn(fakeAccessJwt)

    assertNotAuthenticatedFailure(
      "/token/generate",
      Post()
    )(FakeAuthentication.fakeAnonymousAuthentication)
  }

  behavior of "refreshToken"

  it should "return refresh tokens by mocked JWTService for the authenticated user" in {
    val newFakeAccessJwt = AccessToken("abc.newFakeJWTToken.abc")
    val newFakeRefreshJwt = RefreshToken("ab.newFakeJWTToken.cd")

    when(jwtService.refreshTokens(fakeAccessJwt, fakeRefreshJwt)).thenReturn((newFakeAccessJwt, newFakeRefreshJwt))
    when(jwtService.getConfiguredRefreshExpDuration).thenReturn(refreshDuration)

    assertExpectedResponseFields(
      "/token/refresh",
      Post(
        body = Some(s"""{"token": "${fakeAccessJwt.token}", "refresh": "${fakeRefreshJwt.token}"}""")
      )
    )(
      expectedJsonBody = s"""{"token": "${newFakeAccessJwt.token}", "refresh": "${newFakeRefreshJwt.token}"}"""
    )(auth = None)
  }

  it should "return 400 if bad tokens are supplied" in {
    when(jwtService.refreshTokens(fakeAccessJwt, fakeRefreshJwt))
      .thenThrow(new MalformedJwtException("sign fail desc"))

    assertErrorStatusAndResultBodyJsonEquals(
      "/token/refresh",
      Post(
        body = Some(s"""{"token": "${fakeAccessJwt.token}", "refresh": "${fakeRefreshJwt.token}"}""")
      ),
      expectedStatus = 400,
      expectedJson =
        s"""{
         |    "message": "sign fail desc"
         |}""".stripMargin
    )(auth = None)
  }
  it should "return 401 if invalid tokens are supplied" in {
    when(jwtService.refreshTokens(fakeAccessJwt, fakeRefreshJwt)).thenThrow(new ExpiredJwtException(null, null, "expired jwt"))

    assertErrorStatusAndResultBodyJsonEquals(
      "/token/refresh",
      Post(
        body = Some(s"""{"token": "${fakeAccessJwt.token}", "refresh": "${fakeRefreshJwt.token}"}""")
      ),
      expectedStatus = 401,
      expectedJson =  s"""{
         |    "message": "expired jwt"
         |}""".stripMargin
    )(auth = None)
  }


  behavior of "getPublicKey"

  it should "return a Base64 encoded public key from JWTService when user is authenticated" in {
    val publicKey = Keys.keyPairFor(SignatureAlgorithm.RS256).getPublic
    when(jwtService.publicKey).thenReturn(publicKey)

    val expectedPublicKeyBase64 = Base64.getEncoder.encodeToString(publicKey.getEncoded)

    assertExpectedResponseFields(
      "/token/public-key",
      Get())(
        expectedJsonBody = s"""{"key": "$expectedPublicKeyBase64"}"""
    )(auth = None)
  }

  it should "return a Base64 encoded public key from JWTService when user is not authenticated" in {
    val publicKey = Keys.keyPairFor(SignatureAlgorithm.RS256).getPublic
    when(jwtService.publicKey).thenReturn(publicKey)

    val expectedPublicKeyBase64 = Base64.getEncoder.encodeToString(publicKey.getEncoded)

    assertExpectedResponseFields(
      "/token/public-key",
      Get())(
      expectedJsonBody = s"""{"key": "$expectedPublicKeyBase64"}"""
    )(auth = None)
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

    assertExpectedResponseFields(
      "/token/public-key-jwks",
      Get())(
      expectedJsonBody = expectedResponse
    )(auth = None)
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

    assertExpectedResponseFields(
      "/token/public-key-jwks",
      Get())(
      expectedJsonBody = expectedResponse
    )(auth = None)
  }
}
