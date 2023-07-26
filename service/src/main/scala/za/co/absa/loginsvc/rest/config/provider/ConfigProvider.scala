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

package za.co.absa.loginsvc.rest.config.provider

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import pureconfig._
import pureconfig.generic.auto._
import pureconfig.module.yaml._
import za.co.absa.loginsvc.rest.config.actuator.GitConfig
import za.co.absa.loginsvc.rest.config.auth._
import za.co.absa.loginsvc.rest.config.validation.ConfigValidationException
import za.co.absa.loginsvc.rest.config.{BaseConfig, JwtConfig}

@Component
class ConfigProvider(@Value("${spring.config.location}") yamlPath: String)
  extends JwtConfigProvider
    with AuthConfigProvider {

  private val logger = LoggerFactory.getLogger(classOf[ConfigProvider])
  private val yamlConfig: YamlConfigSource = YamlConfigSource.file(yamlPath)

  if(yamlConfig.value().isRight) logger.info(s"Config File successfully loaded from $yamlPath")
  else throw ConfigValidationException(s"Config File could not be loaded from $yamlPath")

  //GitConfig needs to be initialized at startup
  getGitConfig

  def getBaseConfig : BaseConfig = {
    createConfigClass[BaseConfig]("loginsvc.rest.config").
      getOrElse(BaseConfig(""))
  }

  def getJWTConfig : JwtConfig = {
    val jwtConfig = createConfigClass[JwtConfig]("loginsvc.rest.jwt").
      getOrElse(throw ConfigValidationException("Error with JWT Config properties"))
    jwtConfig.throwErrors()

    jwtConfig
  }

  def getLdapConfig : ActiveDirectoryLDAPConfig = {
    val ldapConfigOption = createConfigClass[ActiveDirectoryLDAPConfig]("loginsvc.rest.auth.provider.ldap")
    if(ldapConfigOption.nonEmpty)
      {
        val ldapConfig = ldapConfigOption.get
        ldapConfig.throwErrors()
        ldapConfig
      }
    else ActiveDirectoryLDAPConfig("", "", "", 0)
  }

  def getUsersConfig : UsersConfig = {
    val userConfigOption = createConfigClass[UsersConfig]("loginsvc.rest.auth.provider.users")
    if (userConfigOption.nonEmpty) {
      val userConfig = userConfigOption.get
      userConfig.throwErrors()
      userConfig
    }
    else UsersConfig(Array.empty[UserConfig], 0)
  }

  private def getGitConfig: GitConfig = {
    createConfigClass[GitConfig]("loginsvc.rest.config.git-info").
      getOrElse(GitConfig(generateGitProperties = false, generateGitPropertiesFile = false))
  }

  private def createConfigClass[A](nameSpace : String)(implicit reader: ConfigReader[A]) : Option[A] =
    this.yamlConfig.at(nameSpace).load[A].toOption
}
