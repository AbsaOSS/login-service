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

import org.springframework.context.annotation.{Bean, Configuration}
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import za.co.absa.logingw.rest.config.ActiveDirectoryLDAPConfig
import za.co.absa.logingw.rest.provider.DummyAuthenticationProvider
import za.co.absa.logingw.rest.provider.ad.ldap.ActiveDirectoryLDAPAuthenticationProvider

@Configuration
@EnableWebSecurity
class SecurityConfig {

  @Bean
  def filterChain(http: HttpSecurity): SecurityFilterChain = {
    http
      .csrf()
        .disable()
      .cors()
        .and()
      .authorizeRequests()
      .antMatchers(
        "/v3/api-docs*", // /v3/api-docs + /v3/api-docs.yaml
        "/swagger-ui/**", "/swagger-ui.html", // "/swagger-ui.html" redirects to "/swagger-ui/index.html
        "/swagger-resources/**", "/v3/api-docs/**", // swagger needs these
        "/info",
        "/actuator/**",
        "/token/public-key").permitAll()
        .anyRequest().authenticated()
        .and()
      .sessionManagement()
        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        .and()
      .httpBasic()

    http.build()
  }

  @Bean
  def authManager(http: HttpSecurity): AuthenticationManager = {
    val authenticationManagerBuilder = http.getSharedObject(classOf[AuthenticationManagerBuilder])

    // TODO make it autowired but only if in confid AD LDAP provider is set up (#28)
    val adLDAPConfig = ActiveDirectoryLDAPConfig(
      "some.domain.com",
      "ldaps://some.domain.com:636/",
      "(samaccountname={1})"
    )

    // TODO: take which providers and in which order to use from config (#28)
    authenticationManagerBuilder
      // if it is not null, on auth failure infinite recursion happens
      .parentAuthenticationManager(null)
      .authenticationProvider(
        new ActiveDirectoryLDAPAuthenticationProvider(adLDAPConfig)
      )
      .authenticationProvider(
        new DummyAuthenticationProvider()
      )
      .build
  }

}
