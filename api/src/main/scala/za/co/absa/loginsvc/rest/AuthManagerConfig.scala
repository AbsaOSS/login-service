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

package za.co.absa.loginsvc.rest

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.{Bean, Configuration}
import org.springframework.security.authentication.{AuthenticationManager, AuthenticationProvider}
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import za.co.absa.loginsvc.rest.config.auth.{ActiveDirectoryLDAPConfig, ConfigOrdering, UsersConfig}
import za.co.absa.loginsvc.rest.config.provider.AuthConfigProvider
import za.co.absa.loginsvc.rest.config.validation.ConfigValidationException
import za.co.absa.loginsvc.rest.provider.ConfigUsersAuthenticationProvider
import za.co.absa.loginsvc.rest.provider.ad.ldap.ActiveDirectoryLDAPAuthenticationProvider

import scala.collection.immutable.SortedMap

/**
 * This class registers the authManager bean that is responsible for users to be able to "login" using their credentials
 *  based on the config
 * @param authConfigsProvider
 */
//@Configuration
class AuthManagerConfig @Autowired()(authConfigsProvider: AuthConfigProvider){

  private val usersConfig: Option[UsersConfig] = authConfigsProvider.getUsersConfig
  private val adLDAPConfig: Option[ActiveDirectoryLDAPConfig] = authConfigsProvider.getLdapConfig

  private val logger = LoggerFactory.getLogger(classOf[AuthManagerConfig])

  @Bean
  def authManager(http: HttpSecurity): AuthenticationManager = {

    val authenticationManagerBuilder = http.getSharedObject(classOf[AuthenticationManagerBuilder]).parentAuthenticationManager(null)
    val configs: Array[ConfigOrdering] = Array(usersConfig, adLDAPConfig).flatten
    val orderedProviders = createAuthProviders(configs)

    if(orderedProviders.isEmpty)
      throw ConfigValidationException("No authentication method enabled in config")

    orderedProviders.zipWithIndex.foreach { case (authProvider, index) =>
      logger.info(s"Authentication method ${authProvider.getClass.getSimpleName} has been initialized at order ${index + 1}")
      authenticationManagerBuilder.authenticationProvider(authProvider)
    }
    authenticationManagerBuilder.build
  }

  private def createAuthProviders(configs: Array[ConfigOrdering]): Array[AuthenticationProvider] = {
    Array.empty[AuthenticationProvider] ++ configs.filter(_.order > 0).sortBy(_.order)
      .map {
        case c: UsersConfig => new ConfigUsersAuthenticationProvider(c)
        case c: ActiveDirectoryLDAPConfig => new ActiveDirectoryLDAPAuthenticationProvider(c)
        case other => throw new IllegalStateException(s"unsupported config $other")
      }
  }
}
