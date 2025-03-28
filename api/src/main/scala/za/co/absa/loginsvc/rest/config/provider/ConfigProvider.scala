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
import za.co.absa.loginsvc.rest.config.jwt.{AwsSecretsManagerKeyConfig, InMemoryKeyConfig, KeyConfig}
import za.co.absa.loginsvc.rest.config.validation.ConfigValidationException
import za.co.absa.loginsvc.rest.config.BaseConfig

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

  def getJwtKeyConfig : KeyConfig = {
    val inMemoryKeyConfig: Option[InMemoryKeyConfig] = createConfigClass[InMemoryKeyConfig]("loginsvc.rest.jwt.generate-in-memory")
    val awsSecretsManagerKeyConfig: Option[AwsSecretsManagerKeyConfig] = createConfigClass[AwsSecretsManagerKeyConfig]("loginsvc.rest.jwt.aws-secrets-manager")

    (inMemoryKeyConfig, awsSecretsManagerKeyConfig) match {
      case (Some(_), Some(_)) =>
        throw ConfigValidationException("Both inMemoryKeyConfig and awsSecretsManagerKeyConfig exist. Please choose only one.")

      case (None, None) =>
        throw ConfigValidationException("Neither inMemoryKeyConfig nor awsSecretsManagerKeyConfig exists. One of them should exist.")

      case (inMemoryKeyConfig@Some(_), None) =>
        val keyConfig: KeyConfig = inMemoryKeyConfig.get
        keyConfig.throwErrors()

        keyConfig

      case (None, awsSecretsManagerKeyConfig@Some(_)) =>
        val keyConfig: KeyConfig = awsSecretsManagerKeyConfig.get
        keyConfig.throwErrors()

        keyConfig

      case _ =>
        throw ConfigValidationException("Error with current config concerning inMemoryKeyConfig or awsSecretsManagerKeyConfig")
    }
  }

  def getLdapConfig : Option[ActiveDirectoryLDAPConfig] = {
    val ldapConfigOption = createConfigClass[ActiveDirectoryLDAPConfig]("loginsvc.rest.auth.provider.ldap")
    if(ldapConfigOption.nonEmpty)
      ldapConfigOption.get.throwErrors()

    ldapConfigOption
  }

  def getUsersConfig : Option[UsersConfig] = {
    val userConfigOption = createConfigClass[UsersConfig]("loginsvc.rest.auth.provider.users")
    if (userConfigOption.nonEmpty)
      userConfigOption.get.throwErrors()

    userConfigOption
  }

  private def getGitConfig: GitConfig = {
    createConfigClass[GitConfig]("loginsvc.rest.config.git-info").
      getOrElse(GitConfig(generateGitProperties = false, generateGitPropertiesFile = false))
  }

  private def createConfigClass[A](nameSpace : String)(implicit reader: ConfigReader[A]) : Option[A] = {
    val configProperty : ConfigSource = this.yamlConfig.at(nameSpace)
    val configClass : Option[A] = configProperty.load[A].toOption
    if(configProperty.value().isRight && configClass.isEmpty)
      throw ConfigValidationException(s"Config properties $nameSpace found but could not be parsed, please check if correct")

    configClass
  }
}
