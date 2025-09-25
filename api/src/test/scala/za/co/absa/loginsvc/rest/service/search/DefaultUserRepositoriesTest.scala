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

package za.co.absa.loginsvc.rest.service.search

import org.scalamock.scalatest.MockFactory
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import za.co.absa.loginsvc.rest.config.auth.{ActiveDirectoryLDAPConfig, LdapUserCredentialsConfig, ServiceAccountConfig, UserConfig, UsersConfig}
import za.co.absa.loginsvc.rest.config.provider.{AuthConfigProvider, ConfigProvider}
import za.co.absa.loginsvc.rest.config.validation.ConfigValidationException

class DefaultUserRepositoriesTest extends AnyFlatSpec with BeforeAndAfterEach with Matchers with MockFactory {

  private val testConfig: ConfigProvider = new ConfigProvider("api/src/test/resources/application.yaml")
  private val emptyServiceAccount = ServiceAccountConfig("", Option(LdapUserCredentialsConfig("", "")), None, None)

  private val enabledLdapTestConfig = Some(ActiveDirectoryLDAPConfig("", "", "", order = 2, emptyServiceAccount, None, None, None))
  private val enabledUsersConfig = testConfig.getUsersConfig // has order = 1

  private val disabledLdapTestConfig = Some(ActiveDirectoryLDAPConfig("", "", "", order = 0, emptyServiceAccount, None, None, None))
  private val disabledUsersConfig = Some(UsersConfig(Array.empty[UserConfig], order = 0))

  private def createAuthConfigProviderUsing(optLdapConfig: Option[ActiveDirectoryLDAPConfig], optUsersConfig: Option[UsersConfig]): AuthConfigProvider = {
    new AuthConfigProvider {
      override def getLdapConfig: Option[ActiveDirectoryLDAPConfig] = optLdapConfig
      override def getUsersConfig: Option[UsersConfig] = optUsersConfig
    }
  }

  behavior of "DefaultUserRepositories (constructor)"

  it should "fail if no auth config is provided" in {
    val emptyAuthConfigProvider = createAuthConfigProviderUsing(None, None)

    the [ConfigValidationException] thrownBy {
      new DefaultUserRepositories(emptyAuthConfigProvider)
    } should have message "No authentication method enabled in config"
  }

  it should "fail if no enabled (order !=0) auth config is provided" in {
    val noEnabledAuthConfigProvider = createAuthConfigProviderUsing(disabledLdapTestConfig, disabledUsersConfig)

    the [ConfigValidationException] thrownBy {
      new DefaultUserRepositories(noEnabledAuthConfigProvider)
    } should have message "No authentication method enabled in config"
  }

  behavior of "DefaultUserRepositories.orderedProviders"

  it should "correctly create userRepositories object with both ordered configs" in {
    val emptyAuthConfigProvider = createAuthConfigProviderUsing(enabledLdapTestConfig, enabledUsersConfig)

    val userRepositories = new DefaultUserRepositories(emptyAuthConfigProvider)
    userRepositories.orderedProviders.map(_.getClass) shouldBe Seq(classOf[UsersFromConfigRepository], classOf[LdapUserRepository]) // order 1, order 2
  }

  it should "correctly create userRepositories object with both reordered configs" in {
    val emptyAuthConfigProvider = createAuthConfigProviderUsing(enabledLdapTestConfig, enabledUsersConfig.map(_.copy(order = 3)))

    val userRepositories = new DefaultUserRepositories(emptyAuthConfigProvider)
    userRepositories.orderedProviders.map(_.getClass) shouldBe Seq(classOf[LdapUserRepository], classOf[UsersFromConfigRepository]) // order 2, order 3
  }

  it should "correctly create userRepositories object with one enabled config only" in {
    val emptyAuthConfigProvider = createAuthConfigProviderUsing(enabledLdapTestConfig, disabledUsersConfig)

    val userRepositories = new DefaultUserRepositories(emptyAuthConfigProvider)
    userRepositories.orderedProviders.map(_.getClass) shouldBe Seq(classOf[LdapUserRepository])
  }

  it should "correctly create userRepositories object with one enabled config only 2" in {
    val emptyAuthConfigProvider = createAuthConfigProviderUsing(disabledLdapTestConfig, enabledUsersConfig)

    val userRepositories = new DefaultUserRepositories(emptyAuthConfigProvider)
    userRepositories.orderedProviders.map(_.getClass) shouldBe Seq(classOf[UsersFromConfigRepository])
  }

  it should "correctly create userRepositories object with one present config only" in {
    val emptyAuthConfigProvider = createAuthConfigProviderUsing(enabledLdapTestConfig, None)

    val userRepositories = new DefaultUserRepositories(emptyAuthConfigProvider)
    userRepositories.orderedProviders.map(_.getClass) shouldBe Seq(classOf[LdapUserRepository])
  }

  it should "correctly create userRepositories object with one present config only 2" in {
    val emptyAuthConfigProvider = createAuthConfigProviderUsing(None, enabledUsersConfig)

    val userRepositories = new DefaultUserRepositories(emptyAuthConfigProvider)
    userRepositories.orderedProviders.map(_.getClass) shouldBe Seq(classOf[UsersFromConfigRepository])
  }
}
