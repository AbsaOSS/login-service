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

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import za.co.absa.loginsvc.model.User
import za.co.absa.loginsvc.rest.config.auth.{ActiveDirectoryLDAPConfig, LdapRetryConfig, LdapUserCredentialsConfig, ServiceAccountConfig}

class LdapUserRepositoryTest extends AnyFlatSpec with Matchers {

  private val integratedCfg = LdapUserCredentialsConfig("svc-ldap", "password")
  private val serviceAccountCfg = ServiceAccountConfig(
    "CN=%s,OU=Users,OU=Accounts,DC=domain,DC=subdomain,DC=com",
    Option(integratedCfg),
    None)
  private val ldapCfg = ActiveDirectoryLDAPConfig(
    "some.domain.com",
    "ldaps://some.domain.com:636/",
    "SomeAccount",
    1,
    serviceAccountCfg,
    None,
    None,
    None)

  private val testUser: User = User("user",
    Seq("group1", "group2"),
    Map("testAtt" -> None))

  private class TestLdapUserRepository(config: ActiveDirectoryLDAPConfig)
    extends LdapUserRepository(config) {
    var counter: Int = 1
    override def contextSearch(username: String): List[User] = {
      config.ldapRetry.fold(List(testUser))(_ => {
        if(counter < 4) {
          counter += 1
          throw new RuntimeException("TestException")
        }
        else List(testUser)
      })
    }
  }

  "ContextSearch" should "only be called once when ldapRetry is None" in {
    val testLdapUserRepository = new TestLdapUserRepository(ldapCfg)
    val user = testLdapUserRepository.searchForUser(testUser.name)

    assert(testLdapUserRepository.counter == 1)
    assert(user.get == testUser)
  }

  "authenticate" should "be called 4 times before a successful attempt occurs" in {
    val retryConfig = LdapRetryConfig(4, 100)
    val ldapConfig = ldapCfg.copy(ldapRetry = Some(retryConfig))

    val testLdapUserRepository = new TestLdapUserRepository(ldapConfig)
    val user = testLdapUserRepository.searchForUser(testUser.name)

    assert(testLdapUserRepository.counter == 4)
    assert(user.get == testUser)
  }

  "authenticate" should "be fail after 2 retries" in {
    val retryConfig = LdapRetryConfig(2, 100)
    val ldapConfig = ldapCfg.copy(ldapRetry = Some(retryConfig))

    val testLdapUserRepository = new TestLdapUserRepository(ldapConfig)

    assertThrows[RuntimeException] {
      testLdapUserRepository.searchForUser(testUser.name)
    }
    assert(testLdapUserRepository.counter == 3)
  }
}
