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

import io.jsonwebtoken._
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import za.co.absa.loginsvc.model.{PrefixesConfig, User}
import za.co.absa.loginsvc.rest.config.jwt.{InMemoryKeyConfig, KeyConfig}
import za.co.absa.loginsvc.rest.config.provider.{ConfigProvider, JwtConfigProvider}
import za.co.absa.loginsvc.rest.model.Token
import za.co.absa.loginsvc.rest.service.search.{DefaultUserRepositories, UserSearchService}

import java.security.PublicKey
import java.util
import scala.collection.JavaConverters._
import scala.util.Try

/**
 * Tests for generateAccessToken with allowProvidersToRefreshGroupsOnGenerate = true.
 * Separated into its own class to avoid creating/closing an unnecessary default JWTService
 * in beforeEach/afterEach for tests that need a different configuration.
 */
class JWTServiceRefreshGroupsTest extends AnyFlatSpec with BeforeAndAfterEach with Matchers {

  private val testConfig: ConfigProvider = new ConfigProvider("api/src/test/resources/application.yaml")
  private var jwtService: JWTService = _
  private var authSearchService: UserSearchService = _

  private def parseJWT(jwt: Token, publicKey: PublicKey = jwtService.publicKeys._1): Try[Jws[Claims]] = Try {
    Jwts.parserBuilder().setSigningKey(publicKey).build().parseClaimsJws(jwt.token)
  }

  override def beforeEach(): Unit = {
    // just like the test config, but with allowProvidersToRefreshGroupsOnGenerate = true to test the group-refreshing behavior
    authSearchService = new UserSearchService(new DefaultUserRepositories(testConfig))
    val configP = new JwtConfigProvider {
      override def getJwtKeyConfig: KeyConfig = testConfig.getJwtKeyConfig match {
        case inMemoryConfig: InMemoryKeyConfig =>
          inMemoryConfig.copy(allowProvidersToRefreshGroupsOnGenerate = true)
        case other => fail(s"Unexpected KeyConfig type: ${other.getClass.getName}")
      }
    }
    jwtService = new JWTService(configP, authSearchService)
  }

  override def afterEach(): Unit = {
    jwtService.close()
  }

  behavior of "generateToken with allowProvidersToRefreshGroupsOnGenerate=true"

  it should "fetch groups from UserSearchService when inputUser has empty groups and flag is true" in {
    // user2 exists in test config with groups = ["group2"]
    val userWithEmptyGroups = User("user2", Seq.empty, Map("mail" -> Some("user@two.org")))
    val jwt = jwtService.generateAccessToken(userWithEmptyGroups, None)
    val parsedJWT = parseJWT(jwt)

    assert(parsedJWT.isSuccess)
    val actualGroups = parsedJWT.get.getBody.get("groups", classOf[util.ArrayList[String]]).asScala

    actualGroups shouldBe Seq("group2")
  }

  Seq(
    Some(PrefixesConfig(Set("gr"), caseSensitive = true)), // group2 should match
    Some(PrefixesConfig(Set("GR"), caseSensitive = false)), // group2 should match case-insensitively
  ).foreach { prefixConfig =>

    it should s"fetch and filter groups from UserSearchService with prefix config when inputUser has empty groups ($prefixConfig)" in {
      val userWithEmptyGroups = User("user2", Seq.empty, Map("mail" -> Some("user@two.org")))
      val jwt = jwtService.generateAccessToken(userWithEmptyGroups, prefixConfig)
      val parsedJWT = parseJWT(jwt)

      assert(parsedJWT.isSuccess)
      val actualGroups = parsedJWT.get.getBody.get("groups", classOf[util.ArrayList[String]]).asScala

      actualGroups shouldBe Seq("group2")
    }
  }

  it should "fetch and filter groups from UserSearchService resulting in empty when prefix doesn't match" in {
    val userWithEmptyGroups = User("user2", Seq.empty, Map("mail" -> Some("user@two.org")))
    val prefixConfig = Some(PrefixesConfig(Set("nonexistent", "GR"), caseSensitive = true))
    val jwt = jwtService.generateAccessToken(userWithEmptyGroups, prefixConfig)
    val parsedJWT = parseJWT(jwt)

    assert(parsedJWT.isSuccess)
    val actualGroups = parsedJWT.get.getBody.get("groups", classOf[util.ArrayList[String]]).asScala

    actualGroups shouldBe empty
  }

  it should "NOT fetch groups from UserSearchService when inputUser already has groups (flag is true but groups non-empty)" in {
    // user has groups already populated - should use them directly, not fetch from UserSearchService
    val userWithExistingGroups = User("user2", Seq("custom-group"), Map("mail" -> Some("user@two.org")))
    val jwt = jwtService.generateAccessToken(userWithExistingGroups, None)
    val parsedJWT = parseJWT(jwt)

    assert(parsedJWT.isSuccess)
    val actualGroups = parsedJWT.get.getBody.get("groups", classOf[util.ArrayList[String]]).asScala

    actualGroups shouldBe Seq("custom-group")
  }
}

