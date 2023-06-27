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

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.{Bean, Configuration}
import org.springframework.security.authentication.{AuthenticationManager, AuthenticationProvider}
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import za.co.absa.loginsvc.rest.config.auth.{ActiveDirectoryLDAPConfig, UsersConfig}
import za.co.absa.loginsvc.rest.provider.ConfigUsersAuthenticationProvider
import za.co.absa.loginsvc.rest.provider.ad.ldap.ActiveDirectoryLDAPAuthenticationProvider

@Configuration
class AuthManagerConfig{

  @Autowired(required = false)
  private var usersConfig: UsersConfig = _
  @Autowired (required = false)
  private var adLDAPConfig: ActiveDirectoryLDAPConfig = _

  @Bean
  def authManager(http: HttpSecurity): AuthenticationManager = {

    val authenticationManagerBuilder = http.getSharedObject(classOf[AuthenticationManagerBuilder]).parentAuthenticationManager(null)
    val providerMap = Map.newBuilder[Int, AuthenticationProvider]

    if (usersConfig != null && usersConfig.order > 0) {
      val configUsersAuthProvider = new ConfigUsersAuthenticationProvider(usersConfig)
      providerMap += (usersConfig.order -> configUsersAuthProvider)
    }

    if (adLDAPConfig != null && adLDAPConfig.order > 0) {
      val adLDAPAuthProvider = new ActiveDirectoryLDAPAuthenticationProvider(adLDAPConfig)
      providerMap += (adLDAPConfig.order -> adLDAPAuthProvider)
    }

    val orderedProviders = providerMap
      .result()
      .filter(x => x._1 > 0)
      .toList
      .sortBy(_._1)
      .map { case (_, provider) => provider }
      .toArray

    if(orderedProviders.isEmpty)
      throw new Exception("No authentication method enabled in config")

    orderedProviders.foreach(authenticationManagerBuilder.authenticationProvider)
    authenticationManagerBuilder.build
  }
}
