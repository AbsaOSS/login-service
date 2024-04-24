package za.co.absa.loginsvc.rest.provider.kerberos

import org.slf4j.LoggerFactory
import org.springframework.core.io.FileSystemResource
import org.springframework.ldap.core.DirContextOperations
import org.springframework.ldap.core.support.BaseLdapPathContextSource
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.core.{AuthenticationException, GrantedAuthority}
import org.springframework.security.core.authority.{AuthorityUtils, SimpleGrantedAuthority}
import org.springframework.security.kerberos.authentication.KerberosServiceAuthenticationProvider
import org.springframework.security.kerberos.authentication.sun.SunJaasKerberosTicketValidator
import org.springframework.security.kerberos.client.config.SunJaasKrb5LoginConfig
import org.springframework.security.kerberos.client.ldap.KerberosLdapContextSource
import org.springframework.security.kerberos.web.authentication.SpnegoAuthenticationProcessingFilter
import org.springframework.security.ldap.search.FilterBasedLdapUserSearch
import org.springframework.security.ldap.userdetails.{LdapAuthoritiesPopulator, LdapUserDetailsMapper, LdapUserDetailsService}
import org.springframework.security.web.authentication.{AuthenticationFailureHandler, AuthenticationSuccessHandler}

import java.util
import javax.naming.ldap.LdapName
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import scala.collection.JavaConverters._

class KerberosSPNEGOAuthenticationProvider {

  private val logger = LoggerFactory.getLogger(classOf[KerberosSPNEGOAuthenticationProvider])
  logger.debug(s"KerberosSPNEGOAuthenticationProvider init")

  private def sunJaasKerberosTicketValidator(): SunJaasKerberosTicketValidator = {
    val ticketValidator = new SunJaasKerberosTicketValidator()
    ticketValidator.setServicePrincipal("TODO: Insert Service Principal")
    ticketValidator.setKeyTabLocation(new FileSystemResource("TODO: Insert Keytab Loacation"))
    ticketValidator.setDebug(true)
    ticketValidator.afterPropertiesSet()
    ticketValidator
  }

  private def loginConfig(): SunJaasKrb5LoginConfig = {
    val loginConfig = new SunJaasKrb5LoginConfig()
    loginConfig.setServicePrincipal("TODO: Insert Service Principal")
    loginConfig.setKeyTabLocation(new FileSystemResource("TODO: Insert Keytab Loacation"))
    loginConfig.setDebug(true)
    loginConfig.setIsInitiator(true)
    loginConfig.setUseTicketCache(false)
    loginConfig.afterPropertiesSet()
    loginConfig
  }

  private def kerberosLdapContextSource(): KerberosLdapContextSource = {
    val contextSource = new KerberosLdapContextSource("TODO: Add Ldap Server")
    contextSource.setLoginConfig(loginConfig())
    contextSource.afterPropertiesSet()
    contextSource
  }

  private def ldapUserDetailsService(): LdapUserDetailsService = {
    val userSearch = new kerberosLdapSearch("TODO: ldapSearchBase", "TODO: ldapSearchFilter", kerberosLdapContextSource())
    val service = new LdapUserDetailsService(userSearch, new ActiveDirectoryLdapAuthoritiesPopulator)
    service.setUserDetailsMapper(new LdapUserDetailsMapper())
    service
  }

  private def kerberosServiceAuthenticationProvider(): KerberosServiceAuthenticationProvider = {
    val provider = new KerberosServiceAuthenticationProvider()
    provider.setTicketValidator(sunJaasKerberosTicketValidator())
    provider.setUserDetailsService(ldapUserDetailsService())
    provider
  }
}

object RestApiKerberosAuthentication {
  private val logger = LoggerFactory.getLogger(this.getClass)

  def spnegoAuthenticationProcessingFilter(authenticationManager: AuthenticationManager, authenticationSuccessHandler: AuthenticationSuccessHandler): SpnegoAuthenticationProcessingFilter = {
    val filter = new SpnegoAuthenticationProcessingFilter()
    filter.setAuthenticationManager(authenticationManager)
    filter.setSkipIfAlreadyAuthenticated(true)
    filter.setSuccessHandler(authenticationSuccessHandler)
    filter.setFailureHandler((request: HttpServletRequest, response: HttpServletResponse, exception: AuthenticationException) => {
      logger.error(exception.getStackTrace.toString)
    })
    filter
  }
}

private class kerberosLdapSearch(searchBase: String, searchFilter: String, contextSource: BaseLdapPathContextSource)
  extends FilterBasedLdapUserSearch(searchBase, searchFilter, contextSource) {
  override def searchForUser(username: String): DirContextOperations = {
    val user = if(username.contains("@")) {
      username.split("@").head
    }
    else {
      username
    }
    super.searchForUser(user)
  }
}

private class ActiveDirectoryLdapAuthoritiesPopulator() extends LdapAuthoritiesPopulator {
  override def getGrantedAuthorities(userData: DirContextOperations, username: String): util.Collection[_ <: GrantedAuthority] = {
    val groups = userData.getStringAttributes("memberof")

    if(groups.isEmpty)
      AuthorityUtils.NO_AUTHORITIES
    else {
      groups.map({group =>
        val ldapName = new LdapName(group)
        val role = ldapName.getRdn(ldapName.size()-1).getValue.toString
        new SimpleGrantedAuthority(role)
      }).toList.asJava
    }
  }
}