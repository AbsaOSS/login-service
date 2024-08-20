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
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.userdetails.{UserDetails, UserDetailsService}
import za.co.absa.loginsvc.rest.config.auth.ActiveDirectoryLDAPConfig
import za.co.absa.loginsvc.rest.model.KerberosUserDetails
import za.co.absa.loginsvc.rest.service.search.LdapUserRepository

case class KerberosUserDetailsService(activeDirectoryLDAPConfig: ActiveDirectoryLDAPConfig) extends UserDetailsService {

  private val logger = LoggerFactory.getLogger(classOf[KerberosUserDetailsService])

  override def loadUserByUsername(username: String): UserDetails =
  {
    val name = if(username.contains("@")) {
      username.split("@").head
    } else {
      username
    }

    val ldapContext = new LdapUserRepository(activeDirectoryLDAPConfig)
    logger.info(s"Searching for kerberos user:$name")
    val userOption = ldapContext.searchForUser(name)

    if(userOption.isEmpty)
      throw new BadCredentialsException(s"Cannot find kerberos user, $name, in Ldap")

    val user = userOption.get
    logger.info(s"Found Kerberos User: ${user.name}")
    KerberosUserDetails(user)
  }
}

