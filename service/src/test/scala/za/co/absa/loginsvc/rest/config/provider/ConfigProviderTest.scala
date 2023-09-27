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

package za.co.absa.loginsvc.rest.config.provider

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import za.co.absa.loginsvc.rest.config.auth.{ActiveDirectoryLDAPConfig, UsersConfig}
import za.co.absa.loginsvc.rest.config.{BaseConfig, JwtConfig}

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

class ConfigProviderTest extends AnyFlatSpec with Matchers  {

  private val configProvider : ConfigProvider = new ConfigProvider("service/src/test/resources/application.yaml")

  "The baseConfig properties" should "Match" in {
    val baseConfig: BaseConfig = configProvider.getBaseConfig
    assert(baseConfig.someKey == "BETA")
  }

  "The jwtConfig properties" should "Match" in {
    val jwtConfig: JwtConfig  = configProvider.getJWTConfig
    jwtConfig.algName shouldBe "RS256"
    jwtConfig.accessExpTime shouldBe FiniteDuration(10, TimeUnit.MINUTES)
    jwtConfig.refreshExpTime shouldBe FiniteDuration(10, TimeUnit.HOURS)
  }

  "The ldapConfig properties" should "Match" in {
    val activeDirectoryLDAPConfig: ActiveDirectoryLDAPConfig = configProvider.getLdapConfig
    assert(activeDirectoryLDAPConfig.url == "ldaps://some.domain.com:636/" &&
      activeDirectoryLDAPConfig.domain  == "some.domain.com" &&
      activeDirectoryLDAPConfig.searchFilter  == "(samaccountname={1})" &&
      activeDirectoryLDAPConfig.order  == 1)
  }

  "The usersConfig properties" should "be loaded correctly" in {
    val usersConfig: UsersConfig = configProvider.getUsersConfig
    assert(usersConfig.order == 0)

    assert(usersConfig.knownUsers(0).groups(0) == "group1" &&
      usersConfig.knownUsers(0).email.isEmpty &&
      usersConfig.knownUsers(0).displayname.isEmpty &&
      usersConfig.knownUsers(0).password == "password1" &&
      usersConfig.knownUsers(0).username == "user1")

    assert(usersConfig.knownUsers(1).groups(0) == "group2" &&
      usersConfig.knownUsers(1).email == Some("user@two.org") &&
      usersConfig.knownUsers(1).displayname == Some("User Two") &&
      usersConfig.knownUsers(1).password == "password2" &&
      usersConfig.knownUsers(1).username == "user2")
  }
}
