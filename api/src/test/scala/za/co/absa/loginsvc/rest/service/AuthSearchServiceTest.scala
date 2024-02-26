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

import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import za.co.absa.loginsvc.model.User
import za.co.absa.loginsvc.rest.config.auth.{ActiveDirectoryLDAPConfig, IntegratedLdapUserConfig, ServiceAccountConfig, UserConfig, UsersConfig}
import za.co.absa.loginsvc.rest.config.provider.{AuthConfigProvider, ConfigProvider, JwtConfigProvider}
import za.co.absa.loginsvc.rest.config.validation.ConfigValidationException

class AuthSearchServiceTest extends AnyFlatSpec with BeforeAndAfterEach with Matchers {

  private val testConfig: ConfigProvider = new ConfigProvider("api/src/test/resources/application.yaml")
  private val emptyServiceAccount = ServiceAccountConfig("", Option(IntegratedLdapUserConfig("", "")), None)

  private val authConfigProvider: AuthConfigProvider = new AuthConfigProvider {
    override def getLdapConfig: ActiveDirectoryLDAPConfig = ActiveDirectoryLDAPConfig("", "", "", 0, emptyServiceAccount, None)
    override def getUsersConfig: UsersConfig = testConfig.getUsersConfig
  }
  private val authSearchService: AuthSearchService = new AuthSearchService(authConfigProvider)

  private val user: User = User(
    name = "user2",
    groups = Seq("group2"),
    optionalAttributes = Map("mail" -> Some("user@two.org"))
  )

  it should "return a matching user" in {
    val result = authSearchService.searchUser(user.name)
    result.name shouldBe user.name
    result.optionalAttributes.get("mail") shouldBe user.optionalAttributes.get("mail")
    result.groups shouldBe user.groups
  }

  it should "fail if user doesn't exist" in {
    an [NoSuchElementException] should be thrownBy {
      authSearchService.searchUser("nonexistent")
    }
  }

  it should "fail if no auth config is provided" in {
    val emptyAuthConfigProvider: AuthConfigProvider = new AuthConfigProvider {
      override def getLdapConfig: ActiveDirectoryLDAPConfig = ActiveDirectoryLDAPConfig("", "", "", 0, emptyServiceAccount, None)
      override def getUsersConfig: UsersConfig = UsersConfig(Array.empty[UserConfig], 0)
    }

    an [ConfigValidationException] should be thrownBy {
      new AuthSearchService(emptyAuthConfigProvider)
    }
  }
}
