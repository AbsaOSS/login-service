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

package za.co.absa.loginsvc.rest.service.actuator

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.test.context.SpringBootTest
import za.co.absa.loginsvc.rest.config.auth.{ActiveDirectoryLDAPConfig, LdapUserCredentialsConfig, ServiceAccountConfig, UsersConfig}
import za.co.absa.loginsvc.rest.config.provider.AuthConfigProvider
import za.co.absa.loginsvc.rest.service.actuator.LdapHealthService

import javax.naming.CommunicationException
import javax.naming.directory.DirContext

@SpringBootTest
class LdapHealthServiceTest extends AnyFlatSpec with Matchers {

  private val integratedCfg = LdapUserCredentialsConfig("svc-ldap", "password")
  private val serviceAccountCfg = ServiceAccountConfig(
    "CN=%s,OU=Users,OU=Accounts,DC=domain,DC=subdomain,DC=com",
    Option(integratedCfg),
    None)
  private val ldapCfgZeroOrder = ActiveDirectoryLDAPConfig(
    "some.domain.com",
    "ldaps://some.domain.com:636/",
    "SomeAccount",
    0,
    serviceAccountCfg,
    None,
    None,
    None)

  private class testLdapHealthService(authConfigProvider: AuthConfigProvider)
    extends LdapHealthService(authConfigProvider) {
    override private[actuator] def getContext(config:ActiveDirectoryLDAPConfig): DirContext = {
      throw new CommunicationException("some.domain.com:636")
    }
  }

  "LdapHealthService" should "Return Up on Order 0" in {
    val configProvider = new AuthConfigProvider {
      override def getLdapConfig: Option[ActiveDirectoryLDAPConfig] = Some(ldapCfgZeroOrder)

      override def getUsersConfig: Option[UsersConfig] = None
    }
    val ldapHealthService: LdapHealthService = new testLdapHealthService(configProvider)
    val health = ldapHealthService.health()

    health shouldBe Health.up().withDetail("reason", "ldap order parameter is set to 0. ldap is disabled.").build()
  }

  "LdapHealthService" should "Return Up when ActiveDirectoryLDAPConfig is None" in {
    val configProvider = new AuthConfigProvider {
      override def getLdapConfig: Option[ActiveDirectoryLDAPConfig] = None

      override def getUsersConfig: Option[UsersConfig] = None
    }
    val ldapHealthService: LdapHealthService = new testLdapHealthService(configProvider)
    val health = ldapHealthService.health()

    health shouldBe Health.up().withDetail("reason", "ldap authentication not found in configuration. ldap is disabled.").build()
  }

  "LdapHealthService" should "Return Down when Ldap connection fails" in {
    val configProvider = new AuthConfigProvider {
      override def getLdapConfig: Option[ActiveDirectoryLDAPConfig] = Some(ldapCfgZeroOrder.copy(order = 2))

      override def getUsersConfig: Option[UsersConfig] = None
    }
    val ldapHealthService: LdapHealthService = new testLdapHealthService(configProvider)
    val health = ldapHealthService.health()

    health shouldBe Health.down().withDetail("reason", "Failed to connect: some.domain.com:636").build()
  }
}
