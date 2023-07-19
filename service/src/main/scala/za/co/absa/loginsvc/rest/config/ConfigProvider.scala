package za.co.absa.loginsvc.rest.config

import org.springframework.stereotype.Component
import pureconfig.ConfigReader
import pureconfig.generic.auto._
import pureconfig.module.yaml._
import za.co.absa.loginsvc.rest.config.actuator.GitConfig
import za.co.absa.loginsvc.rest.config.auth._
import za.co.absa.loginsvc.rest.config.validation.ConfigValidationException

@Component
class ConfigProvider {

  private val yamlConfig = YamlConfigSource.file("service\\src\\main\\resources\\application.yaml")

  def getBaseConfig : BaseConfig = {
    createConfigClass[BaseConfig]("loginsvc.rest.config").
      getOrElse(BaseConfig(""))
  }

  def getGitConfig : GitConfig = {
    createConfigClass[GitConfig]("loginsvc.rest.config.git-info").
      getOrElse(GitConfig(generateGitProperties = false,generateGitPropertiesFile = false))
  }
  def getJWTConfig : JwtConfig = {
    createConfigClass[JwtConfig]("loginsvc.rest.jwt").
      getOrElse(throw ConfigValidationException("Error with JWT Config properties"))
  }

  def getLdapConfig : ActiveDirectoryLDAPConfig = {
    createConfigClass[ActiveDirectoryLDAPConfig]("loginsvc.rest.auth.provider.ldap").
      getOrElse(ActiveDirectoryLDAPConfig(null, null, null, 0))
  }

  def getUsersConfig : UsersConfig = {
    createConfigClass[UsersConfig]("loginsvc.rest.auth.provider.users").
      getOrElse(UsersConfig(Array.empty[UserConfig], 0))
  }

  private def createConfigClass[A](nameSpace : String)(implicit reader: ConfigReader[A]) : Option[A] =
    this.yamlConfig.at(nameSpace).load[A].toOption
}
