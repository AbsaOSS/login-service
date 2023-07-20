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

package za.co.absa.loginsvc.rest.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import pureconfig._
import pureconfig.generic.auto._
import pureconfig.module.yaml._
import za.co.absa.loginsvc.rest.config.actuator.GitConfig
import za.co.absa.loginsvc.rest.config.auth._
import za.co.absa.loginsvc.rest.config.validation.ConfigValidationException
import java.nio.file.Files

@Component
class ConfigProvider(@Value("service\\src\\main\\resources\\application.yaml") yaml : String, @Value("false") testEnvironment: Boolean) {

  private val yamlConfig: YamlConfigSource = {
    if (testEnvironment)
      YamlConfigSource.string(yaml)
    else
      YamlConfigSource.file(yaml)
  }

  //GitConfig needs to be initialized at startup
  getGitConfig

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
