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
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.authentication.{AuthenticationManager, ProviderManager}
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.{AuthenticationEntryPoint, SecurityFilterChain}
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter
import za.co.absa.loginsvc.rest.config.provider.AuthConfigProvider
import za.co.absa.loginsvc.rest.provider.kerberos.KerberosSPNEGOAuthenticationProvider

import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.access.AccessDeniedHandler
import za.co.absa.loginsvc.rest.provider.ad.ldap.{ActiveDirectoryLDAPAuthenticationProvider, LdapConnectionException}

@Configuration
@EnableWebSecurity
class SecurityConfig @Autowired()(authConfigsProvider: AuthConfigProvider) {

  private val ldapConfig = authConfigsProvider.getLdapConfig.orNull

  val ldapProvider = new ActiveDirectoryLDAPAuthenticationProvider(ldapConfig)
  val providerManager: AuthenticationManager = new ProviderManager(ldapProvider)

  @Bean
  def filterChain(http: HttpSecurity): SecurityFilterChain = {
    http
      //.authenticationProvider(ldapProvider)
      .exceptionHandling()
        .authenticationEntryPoint(customAuthenticationEntryPoint)
        //.accessDeniedHandler(customAccessDeniedHandler)
        .and()
      .csrf()
        .disable()
      .cors()
        .and()
      .authorizeRequests()
//      .antMatchers(
//        "/v3/api-docs*", // /v3/api-docs + /v3/api-docs.yaml
//        "/swagger-ui/**", "/swagger-ui.html", // "/swagger-ui.html" redirects to "/swagger-ui/index.html
//        "/swagger-resources/**", "/v3/api-docs/**", // swagger needs these
//        "/actuator/**",
//        "/token/refresh", // access+refresh JWT in payload, no auth
//        "/token/public-key-jwks",
//        "/token/public-key",
//        "/token/public-keys").permitAll()
        .anyRequest().authenticated()
        .and()
//      .sessionManagement()
//        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
//        .and()
     // .authenticationManager(providerManager)

    // .httpBasic()
    // todo this does not work? vv

    // httpBasic with special handling fo custom exceptions
    .addFilterAt(new BasicAuthenticationFilter(providerManager, customAuthenticationEntryPoint), classOf[BasicAuthenticationFilter])


//    if(ldapConfig != null)
//      {
//        if(ldapConfig.enableKerberos.isDefined)
//        {
//          val kerberos = new KerberosSPNEGOAuthenticationProvider(ldapConfig)
//
//          http.addFilterBefore(
//              kerberos.spnegoAuthenticationProcessingFilter,
//              classOf[BasicAuthenticationFilter])
//              .exceptionHandling()
//              .authenticationEntryPoint((request: HttpServletRequest,
//                                         response: HttpServletResponse,
//                                         authException: AuthenticationException) => {
//                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED)
//                response.addHeader("WWW-Authenticate", """Basic realm="Realm"""")
//                response.addHeader("WWW-Authenticate", "Negotiate")
//              })
//        }
//      }

    http.build()
  }

  private def customAuthenticationEntryPoint: AuthenticationEntryPoint =
    (request: HttpServletRequest, response: HttpServletResponse, authException: AuthenticationException) => {
      authException match {
        case LdapConnectionException(msg, cause) =>
          response.setStatus(HttpServletResponse.SC_GATEWAY_TIMEOUT);
          response.setContentType("application/json");
          response.getWriter().write("{\"error\": \"LDAP connection failed\"}");

        case other =>
          response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      }

    }
//
//  private def customAccessDeniedHandler: AccessDeniedHandler = (request: HttpServletRequest, response: HttpServletResponse, accessDeniedException: AccessDeniedException) => {
//    response.setStatus(HttpServletResponse.SC_FORBIDDEN); // 403
//    response.setContentType("application/json");
//    response.getWriter().write("{\"error\": \"Access Denied\", \"message\": \"" + accessDeniedException.getMessage() + "\"}");
//
//  }

}
