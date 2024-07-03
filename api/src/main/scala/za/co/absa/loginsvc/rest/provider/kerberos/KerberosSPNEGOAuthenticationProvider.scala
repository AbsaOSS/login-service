package za.co.absa.loginsvc.rest.provider.kerberos

import org.slf4j.LoggerFactory
import org.springframework.core.io.FileSystemResource
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.core.authority.{AuthorityUtils, SimpleGrantedAuthority}
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.kerberos.authentication.{KerberosAuthenticationProvider, KerberosServiceAuthenticationProvider}
import org.springframework.security.kerberos.authentication.sun.{SunJaasKerberosClient, SunJaasKerberosTicketValidator}
import org.springframework.security.kerberos.web.authentication.SpnegoAuthenticationProcessingFilter
import za.co.absa.loginsvc.rest.config.auth.ActiveDirectoryLDAPConfig

class KerberosSPNEGOAuthenticationProvider(activeDirectoryLDAPConfig: ActiveDirectoryLDAPConfig) {

  //TODO: Split into Multiple files for neater implementation
  private val serviceAccount = activeDirectoryLDAPConfig.serviceAccount
  private val kerberos = activeDirectoryLDAPConfig.enableKerberos.get
  private val kerberosDebug = kerberos.debug.getOrElse(false)
  private val logger = LoggerFactory.getLogger(classOf[KerberosSPNEGOAuthenticationProvider])
  logger.debug(s"KerberosSPNEGOAuthenticationProvider init")

  System.setProperty("javax.net.debug", kerberosDebug.toString)
  System.setProperty("sun.security.krb5.debug", kerberosDebug.toString)

  if (kerberos.krbFileLocation.nonEmpty) {
    logger.info(s"Using KRB5 CONF from ${kerberos.krbFileLocation}")
    System.setProperty("java.security.krb5.conf", kerberos.krbFileLocation)
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
      ticketValidator.setServicePrincipal(serviceAccount.username)
      ticketValidator.setKeyTabLocation(new FileSystemResource(kerberos.keytabFileLocation))
      ticketValidator.setDebug(true)
      ticketValidator
    }

  private def dummyUserDetailsService = new DummyUserDetailsService
}

class DummyUserDetailsService extends UserDetailsService {
  override def loadUserByUsername(username: String) = new User(username, "{noop}notUsed", true, true, true, true, AuthorityUtils.createAuthorityList("ROLE_USER"))
}
