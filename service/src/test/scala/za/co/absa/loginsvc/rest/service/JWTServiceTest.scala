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

package za.co.absa.loginsvc.rest.service

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.KeyUse
import io.jsonwebtoken.{Claims, Jws, Jwts}
import org.scalatest.flatspec.AnyFlatSpec
import za.co.absa.loginsvc.model.User
import za.co.absa.loginsvc.rest.config.provider.ConfigProvider

import java.util
import scala.util.Try

class JWTServiceTest extends AnyFlatSpec {

  private val testConfig : ConfigProvider = new ConfigProvider("service/src/test/resources/application.yaml")
  private val jwtService: JWTService = new JWTService(testConfig)

  private val userWithoutEmailAndGroups: User = User(
    name = "testUser",
    email = None,
    displayName = None,
    groups = Seq.empty
  )

  private val userWithoutGroups: User = userWithoutEmailAndGroups.copy(
    email = Some("test@gmail.com")
  )

  private val userWithGroups: User = userWithoutGroups.copy(
    groups = Seq("testGroup1", "testGroup2")
  )

  private def parseJWT(jwt: String): Try[Jws[Claims]] = Try {
    Jwts.parserBuilder().setSigningKey(jwtService.publicKey).build().parseClaimsJws(jwt)
  }

  behavior of "generateToken"

  it should "return a JWT that is verifiable by `publicKey`" in {
    val jwt = jwtService.generateAccessToken(userWithoutGroups)
    val parsedJWT = parseJWT(jwt)

    assert(parsedJWT.isSuccess)
  }

  it should "return a JWT with subject equal to User.name" in {
    val jwt = jwtService.generateAccessToken(userWithoutGroups)
    val parsedJWT = parseJWT(jwt)
    val actualSubject = parsedJWT
      .map(_.getBody.getSubject)
      .get

    assert(actualSubject === userWithoutGroups.name)
  }

  it should "return a JWT with email claim equal to User.email if it is not None" in {
    val jwt = jwtService.generateAccessToken(userWithoutGroups)
    val parsedJWT = parseJWT(jwt)

    val actualEmail = parsedJWT
      .map(_.getBody.get("email", classOf[String]))
      .get

    assert(userWithoutGroups.email contains actualEmail)
  }

  it should "return a JWT without email claim if User.email is None" in {
    val jwt = jwtService.generateAccessToken(userWithoutEmailAndGroups)
    val parsedJWT = parseJWT(jwt)

    assert(parsedJWT.isSuccess)
    parsedJWT.foreach { jwt =>
      val actualEmail = jwt.getBody.get("email")
      assert(actualEmail === null)
    }
  }

  it should "return a JWT kid" in {
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

  behavior of "jwks"

  it should "return a JWK that is equivalent to the `publicKey`" in {
    import scala.collection.JavaConverters._

    val publicKey = jwtService.publicKey
    val jwks = jwtService.jwks
    val rsaKey = jwks.getKeys.asScala.head.toRSAKey

    assert(publicKey == rsaKey.toPublicKey)
  }

  it should "return a JWK with parameters" in {
    import scala.collection.JavaConverters._

    val keys = jwtService.jwks.getKeys.asScala
    assert(keys.length == 1, "One JWK is expected to be generated now")

    val jwk = keys.head
    assert(jwk.getAlgorithm == JWSAlgorithm.RS256)
    assert(jwk.getKeyUse == KeyUse.SIGNATURE)
  }
}
