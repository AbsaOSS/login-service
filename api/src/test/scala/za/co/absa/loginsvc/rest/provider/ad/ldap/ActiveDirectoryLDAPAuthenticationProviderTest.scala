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

package za.co.absa.loginsvc.rest.provider.ad.ldap

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{mock, times, verify, when}
import org.mockito.invocation.InvocationOnMock
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.springframework.security.authentication.{
  AuthenticationProvider,
  UsernamePasswordAuthenticationToken}
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.{User, UserDetails}
import za.co.absa.loginsvc.model.{User => CustomUser}
import za.co.absa.loginsvc.rest.config.auth.{
  ActiveDirectoryLDAPConfig,
  LdapRetryConfig,
  LdapUserCredentialsConfig,
  ServiceAccountConfig}

import java.util.Collections

class ActiveDirectoryLDAPAuthenticationProviderTest extends AnyFlatSpec with Matchers {

  private val integratedCfg = LdapUserCredentialsConfig("svc-ldap", "password")
  private val serviceAccountCfg = ServiceAccountConfig(
    "CN=%s,OU=Users,OU=Accounts,DC=domain,DC=subdomain,DC=com",
    Option(integratedCfg),
    None,
    None)
  private val ldapCfgNoRetries = ActiveDirectoryLDAPConfig(
    "some.domain.com",
    "ldaps://some.domain.com:636/",
    "SomeAccount",
    1,
    serviceAccountCfg,
    None,
    None,
    None)

  private val principal = CustomUser("user", List("ROLE_USER"), Map("testAtt" -> None))
  private val mockAuthentication = mock(classOf[Authentication])

  private class TestActiveDirectoryLDAPAuthenticationProviderEventuallySucceeding(config: ActiveDirectoryLDAPConfig, mockAuthenticationProvider: AuthenticationProvider)
    extends ActiveDirectoryLDAPAuthenticationProvider(config) {
    private var counter: Int = 0
    override private[ldap] def createAuthenticationProvider = mockAuthenticationProvider
    when(mockAuthenticationProvider.authenticate(any[Authentication])).thenAnswer((_: InvocationOnMock) => {
      val userDetails: UserDetails = UserDetailsWithExtras(
        new User(
          "user",
          "password",
          Collections.singleton(new SimpleGrantedAuthority("ROLE_USER"))),
        Map("testAtt" -> None))
      val auth = new UsernamePasswordAuthenticationToken(userDetails, "password", userDetails.getAuthorities)
      config.ldapRetry.fold(auth)({ _ =>
        if (counter < 3) {
          counter += 1
          throw new RuntimeException("TestException")
        }
        else auth
      })
    })
  }

  "authenticate" should "only be called once when ldapRetry is None" in {
    val mockAuthenticationProvider = mock(classOf[AuthenticationProvider])
    val testActiveDirectoryLDAPAuthenticationProvider =
      new TestActiveDirectoryLDAPAuthenticationProviderEventuallySucceeding(ldapCfgNoRetries, mockAuthenticationProvider)
    val testUser = testActiveDirectoryLDAPAuthenticationProvider.authenticate(mockAuthentication)

    verify(mockAuthenticationProvider, times(1)).authenticate(mockAuthentication)
    assert(testUser.getPrincipal == principal)
  }

  "authenticate" should "be called 4 times before a successful attempt occurs" in {
    val retryConfig = LdapRetryConfig(3, 100)
    val ldapCfg = ldapCfgNoRetries.copy(ldapRetry = Some(retryConfig))

    val mockAuthenticationProvider = mock(classOf[AuthenticationProvider])
    val testActiveDirectoryLDAPAuthenticationProvider =
      new TestActiveDirectoryLDAPAuthenticationProviderEventuallySucceeding(ldapCfg, mockAuthenticationProvider)
    val testUser = testActiveDirectoryLDAPAuthenticationProvider.authenticate(mockAuthentication)

    verify(mockAuthenticationProvider, times(4)).authenticate(mockAuthentication)
    assert(testUser.getPrincipal == principal)
  }

  "authenticate" should "fail after 2 retries" in {
    val retryConfig = LdapRetryConfig(2, 100)
    val ldapCfg = ldapCfgNoRetries.copy(ldapRetry = Some(retryConfig))

    val mockAuthenticationProvider = mock(classOf[AuthenticationProvider])
    val testActiveDirectoryLDAPAuthenticationProvider =
      new TestActiveDirectoryLDAPAuthenticationProviderEventuallySucceeding(ldapCfg, mockAuthenticationProvider)

    assertThrows[RuntimeException] {
      testActiveDirectoryLDAPAuthenticationProvider.authenticate(mockAuthentication)
    }

    verify(mockAuthenticationProvider, times(3)).authenticate(mockAuthentication)
  }
}
