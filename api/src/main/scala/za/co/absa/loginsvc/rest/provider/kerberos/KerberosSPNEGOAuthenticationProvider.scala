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
import org.springframework.security.authentication.{AuthenticationManager, BadCredentialsException}
import org.springframework.security.core.userdetails.{UserDetails, UserDetailsService}
import org.springframework.security.kerberos.authentication.KerberosServiceAuthenticationProvider
import org.springframework.security.kerberos.authentication.sun.SunJaasKerberosTicketValidator
import org.springframework.security.kerberos.web.authentication.SpnegoAuthenticationProcessingFilter
import za.co.absa.loginsvc.rest.config.auth.ActiveDirectoryLDAPConfig
import za.co.absa.loginsvc.rest.model.KerberosUserDetails
import za.co.absa.loginsvc.rest.service.search.LdapUserRepository

import scala.collection.JavaConverters._

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
    System.setProperty("java.security.krb5.conf", kerberos.krbFileLocation)
    System.setProperty("KRB5RCACHETYPE","none")
  }

  def spnegoAuthenticationProcessingFilter(authenticationManager: AuthenticationManager): SpnegoAuthenticationProcessingFilter =
    {
      val filter: SpnegoAuthenticationProcessingFilter = new SpnegoAuthenticationProcessingFilter()
      filter.setAuthenticationManager(authenticationManager)
      filter.afterPropertiesSet()
      filter
    }

  def kerberosServiceAuthenticationProvider(): KerberosServiceAuthenticationProvider =
    {
      val provider: KerberosServiceAuthenticationProvider = new KerberosServiceAuthenticationProvider()
      provider.setTicketValidator(sunJaasKerberosTicketValidator())
      provider.setUserDetailsService(dummyUserDetailsService)
      provider.afterPropertiesSet()
      provider
    }

  private def sunJaasKerberosTicketValidator(): SunJaasKerberosTicketValidator =
    {
      val ticketValidator: SunJaasKerberosTicketValidator = new SunJaasKerberosTicketValidator()
      ticketValidator.setServicePrincipal(kerberos.spn)
      ticketValidator.setKeyTabLocation(new FileSystemResource(kerberos.keytabFileLocation))
      ticketValidator.setDebug(true)
      ticketValidator.afterPropertiesSet()
      ticketValidator
    }

  private def dummyUserDetailsService = DummyUserDetailsService(ldapConfig)
}

case class DummyUserDetailsService(activeDirectoryLDAPConfig: ActiveDirectoryLDAPConfig) extends UserDetailsService {
  private val logger = LoggerFactory.getLogger(classOf[DummyUserDetailsService])
  override def loadUserByUsername(username: String): UserDetails =
    {
      val userName = if(username.contains("@")) {
        username.split("@").head
      } else {
        username
      }
      val ldapContext = new LdapUserRepository(activeDirectoryLDAPConfig)
      val user = ldapContext.searchForUser(userName)
      if(user.isEmpty)
        throw new BadCredentialsException("Cannot Find User in Ldap")

      logger.info("Found Kerberos User:" + user.get.name)
      KerberosUserDetails(user.get.name, user.get.groups, user.get.optionalAttributes)
    }
}
