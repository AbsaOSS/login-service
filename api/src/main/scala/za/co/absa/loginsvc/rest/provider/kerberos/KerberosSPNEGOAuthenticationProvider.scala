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

package za.co.absa.loginsvc.rest.provider.kerberos

import org.slf4j.LoggerFactory
import org.springframework.core.io.FileSystemResource
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.core.userdetails.{User, UserDetails, UserDetailsService}
import org.springframework.security.kerberos.authentication.{KerberosAuthenticationProvider, KerberosServiceAuthenticationProvider}
import org.springframework.security.kerberos.authentication.sun.{SunJaasKerberosClient, SunJaasKerberosTicketValidator}
import org.springframework.security.kerberos.client.config.SunJaasKrb5LoginConfig
import org.springframework.security.kerberos.client.ldap.KerberosLdapContextSource
import org.springframework.security.kerberos.web.authentication.SpnegoAuthenticationProcessingFilter
import org.springframework.security.ldap.search.FilterBasedLdapUserSearch
import org.springframework.security.ldap.userdetails.{LdapUserDetailsMapper, LdapUserDetailsService}
import za.co.absa.loginsvc.rest.config.auth.ActiveDirectoryLDAPConfig

class KerberosSPNEGOAuthenticationProvider(activeDirectoryLDAPConfig: ActiveDirectoryLDAPConfig) {

  //TODO: Split into Multiple files for neater implementation
  private val ldapConfig = activeDirectoryLDAPConfig
  private val kerberos = ldapConfig.enableKerberos.get
  private val kerberosDebug = kerberos.debug.getOrElse(false)
  private val logger = LoggerFactory.getLogger(classOf[KerberosSPNEGOAuthenticationProvider])
  logger.debug(s"KerberosSPNEGOAuthenticationProvider init")

  System.setProperty("javax.net.debug", kerberosDebug.toString)
  System.setProperty("sun.security.krb5.debug", kerberosDebug.toString)

  if (kerberos.krbFileLocation.nonEmpty) {
    logger.info(s"Using KRB5 CONF from ${kerberos.krbFileLocation}")
    System.setProperty("java.security.krb5.ini", kerberos.krbFileLocation)
  }

  def kerberosAuthenticationProvider(): KerberosAuthenticationProvider =
    {
      val provider: KerberosAuthenticationProvider  = new KerberosAuthenticationProvider()
      val client: SunJaasKerberosClient = new SunJaasKerberosClient()

      client.setDebug(true)
      provider.setKerberosClient(client)
      provider.setUserDetailsService(dummyUserDetailsService)
      provider
    }

  def spnegoAuthenticationProcessingFilter(authenticationManager: AuthenticationManager): SpnegoAuthenticationProcessingFilter =
    {
      val filter: SpnegoAuthenticationProcessingFilter = new SpnegoAuthenticationProcessingFilter()
      filter.setAuthenticationManager(authenticationManager)
      filter
    }

  def kerberosServiceAuthenticationProvider(): KerberosServiceAuthenticationProvider =
    {
      val provider: KerberosServiceAuthenticationProvider = new KerberosServiceAuthenticationProvider()
      provider.setTicketValidator(sunJaasKerberosTicketValidator())
      provider.setUserDetailsService(dummyUserDetailsService)
      provider
    }

  private def sunJaasKerberosTicketValidator(): SunJaasKerberosTicketValidator =
    {
      val ticketValidator: SunJaasKerberosTicketValidator = new SunJaasKerberosTicketValidator()
      ticketValidator.setServicePrincipal(kerberos.spn)
      ticketValidator.setKeyTabLocation(new FileSystemResource(kerberos.keytabFileLocation))
      ticketValidator.setDebug(true)
      ticketValidator
    }

  private def loginConfig(): SunJaasKrb5LoginConfig = {
    val loginConfig = new SunJaasKrb5LoginConfig()
    loginConfig.setServicePrincipal(kerberos.spn)
    loginConfig.setKeyTabLocation(new FileSystemResource(kerberos.keytabFileLocation))
    loginConfig.setDebug(kerberosDebug)
    loginConfig.setIsInitiator(true)
    loginConfig.setUseTicketCache(false)
    loginConfig.afterPropertiesSet()
    loginConfig
  }

  private def kerberosLdapContextSource(): KerberosLdapContextSource = {
    val contextSource = new KerberosLdapContextSource(ldapConfig.url)
    contextSource.setLoginConfig(loginConfig())
    contextSource.afterPropertiesSet()
    contextSource
  }

  private def ldapUserDetailsService() = {
    val userSearch = new FilterBasedLdapUserSearch(activeDirectoryLDAPConfig.domain, activeDirectoryLDAPConfig.searchFilter, kerberosLdapContextSource())
    val service = new LdapUserDetailsService(userSearch, new ActiveDirectoryLdapAuthoritiesPopulator())
    service.setUserDetailsMapper(new LdapUserDetailsMapper())
    service
  }

  private def dummyUserDetailsService = new DummyUserDetailsService
}

class DummyUserDetailsService extends UserDetailsService {
  private val logger = LoggerFactory.getLogger(classOf[DummyUserDetailsService])
  override def loadUserByUsername(username: String): UserDetails =
    {
      logger.info(s"returning dummy of $username")
      new User(username, "{noop}notUsed", true, true, true, true, AuthorityUtils.createAuthorityList("ROLE_USER"))
    }
}
