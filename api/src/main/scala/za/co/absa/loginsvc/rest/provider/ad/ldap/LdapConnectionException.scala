package za.co.absa.loginsvc.rest.provider.ad.ldap

import org.springframework.security.core.AuthenticationException

case class LdapConnectionException(msg: String, cause: Throwable) extends AuthenticationException(msg, cause)
