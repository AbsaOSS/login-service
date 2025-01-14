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

package za.co.absa.loginsvc.rest.service.jwt

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.KeyUse
import io.jsonwebtoken._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import za.co.absa.loginsvc.model.User
import za.co.absa.loginsvc.rest.config.jwt.{InMemoryKeyConfig, KeyConfig}
import za.co.absa.loginsvc.rest.config.provider.{ConfigProvider, JwtConfigProvider}
import za.co.absa.loginsvc.rest.model.{AccessToken, RefreshToken, Token}
import za.co.absa.loginsvc.rest.service.search.{DefaultUserRepositories, UserSearchService}

import java.security.PublicKey
import java.util
import scala.collection.JavaConverters._
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success, Try}

class JWTServiceTest extends AnyFlatSpec with BeforeAndAfterEach with Matchers {

  private val testConfig : ConfigProvider = new ConfigProvider("api/src/test/resources/application.yaml")
  private var jwtService: JWTService = _
  private var authSearchService: UserSearchService = _

  private val userWithoutEmailAndGroups: User = User(
    name = "user2",
    groups = Seq.empty,
    optionalAttributes = Map.empty
  )

  private val userWithoutGroups: User = userWithoutEmailAndGroups.copy(
    optionalAttributes = Map("mail" -> Some("user@two.org"))
  )

  private val userWithGroups: User = userWithoutGroups.copy(
    groups = Seq("group2")
  )

  private def parseJWT(jwt: Token, publicKey: PublicKey = jwtService.publicKey._1): Try[Jws[Claims]] = Try {
    Jwts.parserBuilder().setSigningKey(publicKey).build().parseClaimsJws(jwt.token)
  }

  override def beforeEach(): Unit = {
    authSearchService = new UserSearchService(new DefaultUserRepositories(testConfig))
    jwtService = new JWTService(testConfig, authSearchService)
  }

  override def afterEach(): Unit = {
    jwtService.close()
  }

  behavior of "generateToken"

  it should "return an access JWT that is verifiable by `publicKey`" in {
    val jwt = jwtService.generateAccessToken(userWithoutGroups)
    val parsedJWT = parseJWT(jwt)

    assert(parsedJWT.isSuccess)
  }

  it should "return an access JWT with subject equal to User.name and has type access" in {
    val jwt = jwtService.generateAccessToken(userWithoutGroups)
    val parsedJWT = parseJWT(jwt)
    assert(parsedJWT.isSuccess)

    parsedJWT match {
      case Success(validJwt) =>
        validJwt.getBody.getSubject shouldBe userWithoutGroups.name
        validJwt.getBody.get("type", classOf[String]) shouldBe "access"

      case Failure(t) => fail("Invalid access JWT", t)
    }

  }

  it should "return an access JWT with email claim equal to User.email if it is not None" in {
    val jwt = jwtService.generateAccessToken(userWithoutGroups)
    val parsedJWT = parseJWT(jwt)

    val actualEmail = parsedJWT
      .map(_.getBody.get("mail", classOf[String]))
      .get

    userWithoutGroups.optionalAttributes("mail").get shouldBe actualEmail
  }

  it should "return an access JWT without email claim if User.email is None" in {
    val jwt = jwtService.generateAccessToken(userWithoutEmailAndGroups)
    val parsedJWT = parseJWT(jwt)

    assert(parsedJWT.isSuccess)
    parsedJWT.foreach { jwt =>
      val actualEmail = jwt.getBody.get("email")
      assert(actualEmail === null)
    }
  }

  it should "return an access JWT kid" in {
    val jwt = jwtService.generateAccessToken(userWithoutEmailAndGroups)
    val parsedJWT = parseJWT(jwt)

    assert(parsedJWT.isSuccess)
    parsedJWT.foreach { jwt =>
      val kid = jwt.getBody.get("kid")
      assert(kid === jwtService.publicKeyThumbprint)
    }
  }

  it should "turn groups into empty `groups` claim for user without groups" in {
    import scala.collection.JavaConverters._

    val jwt = jwtService.generateAccessToken(userWithoutGroups)
    val parsedJWT = parseJWT(jwt)
    val actualGroups = parsedJWT
      .map(_.getBody.get("groups", classOf[util.ArrayList[String]]))
      .get
      .asScala

    assert(actualGroups === userWithoutGroups.groups)
  }

  it should "turn groups into non-empty `groups` claim for user with groups" in {
    import scala.collection.JavaConverters._

    val jwt = jwtService.generateAccessToken(userWithGroups)
    val parsedJWT = parseJWT(jwt)
    val actualGroups = parsedJWT
      .map(_.getBody.get("groups", classOf[util.ArrayList[String]]))
      .get
      .asScala

    assert(actualGroups === userWithGroups.groups)
  }

  behavior of "generateRefreshToken"

  it should "return a refresh JWT that is verifiable by `publicKey`" in {
    val jwt = jwtService.generateRefreshToken(userWithoutGroups)
    val parsedJWT = parseJWT(jwt)

    assert(parsedJWT.isSuccess)
  }

  it should "return an refresh JWT with subject equal to User.name and has type refresh" in {
    val jwt = jwtService.generateRefreshToken(userWithoutGroups)
    val parsedJWT = parseJWT(jwt)
    assert(parsedJWT.isSuccess)

    parsedJWT match {
      case Success(validJwt) =>
        validJwt.getBody.getSubject shouldBe userWithoutGroups.name
        validJwt.getBody.get("type", classOf[String]) shouldBe "refresh"

      case Failure(t) => fail("Invalid refresh JWT", t)
    }
  }

  behavior of "refreshToken"

  it should "refresh a still-valid access JWT token using a valid refresh one - happy scenario" in {
    val accessJwt = jwtService.generateAccessToken(userWithGroups)
    val refreshJwt = jwtService.generateRefreshToken(userWithGroups)

    val (refreshedAccessJwt, _) = jwtService.refreshTokens(accessJwt, refreshJwt)
    val parsedRefreshedAccessJWT = parseJWT(refreshedAccessJwt)
    assert(parsedRefreshedAccessJWT.isSuccess)

    parsedRefreshedAccessJWT match {
      case Success(validJwt) =>
        val jwtBody = validJwt.getBody

        jwtBody.getSubject shouldBe userWithGroups.name
        jwtBody.get("type", classOf[String]) shouldBe "access"
        Option(jwtBody.get("mail", classOf[String])) shouldBe userWithGroups.optionalAttributes.getOrElse("mail", None)
        jwtBody.get("groups", classOf[java.util.List[String]]).asScala shouldBe userWithGroups.groups

      case Failure(t) => fail(s"Invalid refreshed access JWT: $t", t)
    }
  }

  it should "fail with a unreadable tokens" in {
    an[MalformedJwtException] should be thrownBy {
      jwtService.refreshTokens(AccessToken("abc.def.ghi"), RefreshToken("123.456.789"))
    }
  }

  def customTimedJwtService(accessExpTime: FiniteDuration, refreshExpTime: FiniteDuration): JWTService = {
    val configP = new JwtConfigProvider {
      override def getJwtKeyConfig: KeyConfig = InMemoryKeyConfig(
        "RS256", accessExpTime, refreshExpTime, None, None
      )
    }

    new JWTService(configP, authSearchService)
  }

  import scala.concurrent.duration._

  it should "refresh an expired access JWT token using a valid refresh one - common scenario" in {
    val customJwtService = customTimedJwtService(3.seconds, 20.minutes)

    val accessJwt = customJwtService.generateAccessToken(userWithGroups)
    val refreshJwt = customJwtService.generateRefreshToken(userWithGroups)

    Thread.sleep(3 * 1000) // make sure that access is past due - as set above
    parseJWT(accessJwt, customJwtService.publicKey._1).isFailure shouldBe true // expired

    val (refreshedAccessJwt, _) = customJwtService.refreshTokens(accessJwt, refreshJwt)
    val parsedRefreshedAccessJWT = parseJWT(refreshedAccessJwt, customJwtService.publicKey._1)
    assert(parsedRefreshedAccessJWT.isSuccess)

    parsedRefreshedAccessJWT match {
      case Success(validJwt) =>
        val jwtBody = validJwt.getBody

        jwtBody.getSubject shouldBe userWithGroups.name
        jwtBody.get("type", classOf[String]) shouldBe "access"
        Option(jwtBody.get("mail", classOf[String])) shouldBe userWithGroups.optionalAttributes.getOrElse("mail", None)
        jwtBody.get("groups", classOf[java.util.List[String]]).asScala shouldBe userWithGroups.groups

      case Failure(t) => fail(s"Invalid refreshed access JWT: $t", t)
    }
  }

  it should "refuse to refresh an access JWT token using an expired refresh token - day-after scenario" in {
    val customJwtService = customTimedJwtService(1.seconds, 2.seconds)

    val accessJwt = customJwtService.generateAccessToken(userWithGroups)
    val refreshJwt = customJwtService.generateRefreshToken(userWithGroups)

    Thread.sleep(2 * 1000) // make sure that refresh is past due - as set above
    parseJWT(refreshJwt, customJwtService.publicKey._1).isFailure shouldBe true // expired

    an[ExpiredJwtException] should be thrownBy {
      customJwtService.refreshTokens(accessJwt, refreshJwt)
    }

  }

  behavior of "jwks"

  it should "return a JWK that is equivalent to the `publicKey`" in {
    import scala.collection.JavaConverters._

    val publicKey = jwtService.publicKey
    val jwks = jwtService.jwks
    val rsaKey = jwks.getKeys.asScala.head.toRSAKey

    assert(publicKey._1 == rsaKey.toPublicKey)
  }

  it should "return a JWK with parameters" in {
    import scala.collection.JavaConverters._

    val keys = jwtService.jwks.getKeys.asScala
    assert(keys.length == 1, "One JWK is expected to be generated now")

    val jwk = keys.head
    assert(jwk.getAlgorithm == JWSAlgorithm.RS256)
    assert(jwk.getKeyUse == KeyUse.SIGNATURE)
  }

  behavior of "keyRotation"

  it should "rotate an public and private keys after 5 seconds" in {
    val initToken = jwtService.generateAccessToken(userWithoutGroups)
    val initPublicKey = jwtService.publicKey

    Thread.sleep(6 * 1000)
    val refreshedToken = jwtService.generateAccessToken(userWithoutGroups)

    assert(parseJWT(initToken).isFailure)
    assert(parseJWT(refreshedToken).isSuccess)
    assert(initPublicKey != jwtService.publicKey)
    assert(initPublicKey._1 != jwtService.publicKey._1)
    assert(initPublicKey._1 == jwtService.publicKey._2.orNull)
  }

  it should "phase out older keys after 8 seconds" in {
    val initToken = jwtService.generateAccessToken(userWithoutGroups)
    val initPublicKey = jwtService.publicKey

    Thread.sleep(6 * 1000)
    val refreshedToken = jwtService.generateAccessToken(userWithoutGroups)

    assert(parseJWT(initToken).isFailure)
    assert(parseJWT(refreshedToken).isSuccess)
    assert(initPublicKey != jwtService.publicKey)
    assert(initPublicKey._1 != jwtService.publicKey._1)
    assert(initPublicKey._1 == jwtService.publicKey._2.orNull)

    Thread.sleep(3 * 1000)
    assert(jwtService.publicKey._2.isEmpty)
  }
}
