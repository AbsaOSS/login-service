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

package za.co.absa.logingw.rest

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.{Bean, Configuration}
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import za.co.absa.logingw.rest.config.{ActiveDirectoryLDAPConfig, UsersConfig}
import za.co.absa.logingw.rest.provider.ConfigUsersAuthenticationProvider
import za.co.absa.logingw.rest.provider.ad.ldap.ActiveDirectoryLDAPAuthenticationProvider

@Configuration
class AuthManagerConfig @Autowired()(
  // TODO make it autowired but only if in config AD LDAP provider is set up (#28)
  usersConfig: UsersConfig,
  adLDAPConfig: ActiveDirectoryLDAPConfig
){

  @Bean
  def authManager(http: HttpSecurity): AuthenticationManager = {
    val authenticationManagerBuilder = http.getSharedObject(classOf[AuthenticationManagerBuilder])

    // TODO: take which providers and in which order to use from config (#28)
    authenticationManagerBuilder
      // if it is not null, on auth failure infinite recursion happens
      .parentAuthenticationManager(null)
      // currently, comment out or reorder the auth providers you want to use - #28
      .authenticationProvider(
        new ConfigUsersAuthenticationProvider(usersConfig)
      )
      .authenticationProvider(
        new ActiveDirectoryLDAPAuthenticationProvider(adLDAPConfig)
      )
      .build
  }

}
