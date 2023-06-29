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
import za.co.absa.loginsvc.rest.config.auth.{ActiveDirectoryLDAPConfig, DynamicAuthOrder, UsersConfig}
import za.co.absa.loginsvc.rest.provider.ConfigUsersAuthenticationProvider
import za.co.absa.loginsvc.rest.provider.ad.ldap.ActiveDirectoryLDAPAuthenticationProvider

import scala.collection.immutable.SortedMap

@Configuration
class AuthManagerConfig{

  @Autowired(required = false)
  private val usersConfig: UsersConfig = null
  @Autowired (required = false)
  private val adLDAPConfig: ActiveDirectoryLDAPConfig = null

  private val logger = LoggerFactory.getLogger(classOf[AuthManagerConfig])

  @Bean
  def authManager(http: HttpSecurity): AuthenticationManager = {

    val authenticationManagerBuilder = http.getSharedObject(classOf[AuthenticationManagerBuilder]).parentAuthenticationManager(null)
    val configs: Array[DynamicAuthOrder] = Array(usersConfig, adLDAPConfig)
    val orderedProviders = createProviders(configs)

    if(orderedProviders.isEmpty)
      throw new Exception("No authentication method enabled in config")

    orderedProviders.foreach(
      auth => {
        logger.info(s"Authentication method ${auth._2.getClass.getSimpleName} has been initialized at order ${auth._1}")
        authenticationManagerBuilder.authenticationProvider(auth._2)
      })
    authenticationManagerBuilder.build
  }

  private def createProviders(configs: Array[DynamicAuthOrder]): SortedMap[Int, AuthenticationProvider] = {
    val resultsMap = SortedMap.empty[Int, AuthenticationProvider] ++ configs.filter(_.order > 0)
      .map {
        case config@(c: UsersConfig) => (config.order, new ConfigUsersAuthenticationProvider(c))
        case config@(c: ActiveDirectoryLDAPConfig) => (config.order, new ActiveDirectoryLDAPAuthenticationProvider(c))
        case _ => throw new IllegalStateException("unsupported config type")
      }
    resultsMap.range(1, 999)
  }
}
