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

package za.co.absa.logingw.rest.config

import org.junit.jupiter.api.Test
import org.scalatest.matchers.should.Matchers.convertToAnyShouldWrapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource

@SpringBootTest
@TestPropertySource(locations = Array("classpath:application.yaml"))
class ConfigsTest {

  @Autowired
  var baseConfig: BaseConfig = _

  @Autowired
  var jwtConfig: JwtConfig  = _

  @Autowired
  var usersConfig: UsersConfig = _

  @Autowired
  var activeDirectoryLDAPConfig: ActiveDirectoryLDAPConfig = _

  @Test
  def testBaseConfig(): Unit = {
    baseConfig.someKey shouldBe "BETA"
  }

  @Test
  def testJwtConfig(): Unit = {
    jwtConfig.expTime shouldBe 4
    jwtConfig.algName shouldBe "RS256"
  }

  @Test
  def testUserConfig(): Unit = {
    val actual = usersConfig.knownUsers
    val expected = Array(UserConfig("user1", "password1", null, Array()), UserConfig("TestUser", "password123", "test@abs.com", Array("groupA", "groupB")))
    Range(0,actual.length).foreach{i =>
      actual(i).username shouldBe  expected(i).username
      actual(i).password shouldBe  expected(i).password
      actual(i).email shouldBe  expected(i).email
      actual(i).groups.toList shouldBe  expected(i).groups.toList
    }
  }

  @Test
  def testActiveDirectoryLDAPConfig(): Unit = {
    activeDirectoryLDAPConfig.domain shouldBe "some.domain.com"
    activeDirectoryLDAPConfig.url shouldBe "ldaps://some.domain.com:636/"
    activeDirectoryLDAPConfig.searchFilter shouldBe "(samaccountname={1})"
  }

}
