package za.co.absa.loginsvc.rest.actuator

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.test.context.SpringBootTest
import za.co.absa.loginsvc.rest.config.auth.{ActiveDirectoryLDAPConfig, LdapUserCredentialsConfig, ServiceAccountConfig, UsersConfig}
import za.co.absa.loginsvc.rest.config.provider.AuthConfigProvider
import za.co.absa.loginsvc.rest.service.actuator.LdapHealthService

@SpringBootTest
class LdapHealthServiceTest extends AnyFlatSpec with Matchers {

  private val integratedCfg = LdapUserCredentialsConfig("svc-ldap", "password")
  private val serviceAccountCfg = ServiceAccountConfig(
    "CN=%s,OU=Users,OU=Accounts,DC=domain,DC=subdomain,DC=com",
    Option(integratedCfg),
    None)
  private val ldapCfgZeroOrder = ActiveDirectoryLDAPConfig(
    "some.domain.com",
    "ldaps://some.domain.com:636/",
    "SomeAccount",
    0,
    serviceAccountCfg,
    None,
    None,
    None)

  "LdapHealthService" should "Return Up on Order 0" in {
    val configProvider = new AuthConfigProvider {
      override def getLdapConfig: Option[ActiveDirectoryLDAPConfig] = Some(ldapCfgZeroOrder)

      override def getUsersConfig: Option[UsersConfig] = None
    }
    val ldapHealthService: LdapHealthService = new LdapHealthService(configProvider)
    val health = ldapHealthService.health()

    health shouldBe Health.up().withDetail("reason", "ldap order parameter is set to 0, setting it to disabled").build()
  }

  "LdapHealthService" should "Return Up when ActiveDirectoryLDAPConfig is None" in {
    val configProvider = new AuthConfigProvider {
      override def getLdapConfig: Option[ActiveDirectoryLDAPConfig] = None

      override def getUsersConfig: Option[UsersConfig] = None
    }
    val ldapHealthService: LdapHealthService = new LdapHealthService(configProvider)
    val health = ldapHealthService.health()

    health shouldBe Health.up().withDetail("reason", "ldap authentication not found in configuration, setting it to disabled").build()
  }

  "LdapHealthService" should "Return Down when Ldap connection fails" in {
    val configProvider = new AuthConfigProvider {
      override def getLdapConfig: Option[ActiveDirectoryLDAPConfig] = Some(ldapCfgZeroOrder.copy(order = 2))

      override def getUsersConfig: Option[UsersConfig] = None
    }
    val ldapHealthService: LdapHealthService = new LdapHealthService(configProvider)
    val health = ldapHealthService.health()

    health shouldBe Health.down().withDetail("reason", "Failed to connect: some.domain.com:636").build()
  }


}
