package za.co.absa.loginsvc.rest.service.actuator

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.actuate.health.{Health, HealthIndicator}
import org.springframework.stereotype.Component
import za.co.absa.loginsvc.rest.config.provider.AuthConfigProvider

import java.util
import javax.naming.{Context, NamingException}
import javax.naming.directory.{DirContext, InitialDirContext}

@Component("ldapHealthIndicator")
class LdapHealthService @Autowired() (authConfigsProvider: AuthConfigProvider) extends HealthIndicator {
  override def health(): Health = {
    val activeDirectoryLDAPConfig = authConfigsProvider.getLdapConfig
    activeDirectoryLDAPConfig
      .fold(Health.up().withDetail("reason", "ldap authentication not found in configuration, setting it to disabled").build()) (config =>
        if(config.order <= 0)
          {
            Health.up().withDetail("reason", "ldap order parameter is set to 0, setting it to disabled").build()
          }
        else
          {
            val env = new util.Hashtable[String, String]
            env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory")
            env.put(Context.PROVIDER_URL, config.url)
            env.put(Context.SECURITY_AUTHENTICATION, "simple")
            env.put(Context.SECURITY_PRINCIPAL, config.serviceAccount.username)
            env.put(Context.SECURITY_CREDENTIALS, config.serviceAccount.password)

            try {
              val ctx: DirContext = new InitialDirContext(env)
              ctx.close()
              Health.up().withDetail("reason", s"Successfully connected to ${config.url}").build()
            }
            catch {
              case e: NamingException =>
                Health.down().withDetail("reason", s"Failed to connect: ${e.getMessage}").build()
            }
          })
  }
}
