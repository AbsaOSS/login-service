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
    logger.info(s"Searching for user:$name")
    val userOption = ldapContext.searchForUser(name)

    if(userOption.isEmpty)
      throw new BadCredentialsException(s"Cannot Find User, $name, in Ldap")

    val user = userOption.get
    logger.info(s"Found Kerberos User: ${user.name}")
    KerberosUserDetails(user)
  }
}

