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
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter
import org.springframework.security.web.{AuthenticationEntryPoint, SecurityFilterChain}
import za.co.absa.loginsvc.rest.config.provider.AuthConfigProvider
import za.co.absa.loginsvc.rest.provider.ad.ldap.LdapConnectionException
import za.co.absa.loginsvc.rest.provider.kerberos.KerberosSPNEGOAuthenticationProvider

import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import org.springframework.security.core.AuthenticationException
import org.springframework.security.kerberos.web.authentication.SpnegoEntryPoint

@Configuration
@EnableWebSecurity
class SecurityConfig @Autowired()(authConfigsProvider: AuthConfigProvider, authManager: AuthenticationManager) {

  private val ldapConfig = authConfigsProvider.getLdapConfig.orNull
  private val isKerberosEnabled = authConfigsProvider.getLdapConfig.exists(_.enableKerberos.isDefined)

  @Bean
  def spnegoEntryPoint(): SpnegoEntryPoint = {
    new SpnegoEntryPoint("/token/experimental/get-generate")
  }

    /* ???
    .exceptionHandling()
    .authenticationEntryPoint(spnegoEntryPoint())
    .and()
     */

  @Bean
  def filterChain(http: HttpSecurity): SecurityFilterChain = {
    http
      .exceptionHandling()
        .authenticationEntryPoint(customAuthenticationEntryPoint)
        .and()
      .csrf()
        .disable()
      .cors()
        .and()
      .authorizeRequests()
      .antMatchers(
        "/v3/api-docs*", // /v3/api-docs + /v3/api-docs.yaml
        "/swagger-ui/**", "/swagger-ui.html", // "/swagger-ui.html" redirects to "/swagger-ui/index.html
        "/swagger-resources/**", "/v3/api-docs/**", // swagger needs these
        "/actuator/**",
        "/token/refresh", // access+refresh JWT in payload, no auth
        "/token/public-key-jwks",
        "/token/public-key",
        "/token/public-keys").permitAll()
        .anyRequest().authenticated()
        .and()
      .sessionManagement()
        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        .and()
      // like "httpBasic", but with special handling fo custom exceptions:
      .addFilterAt(new BasicAuthenticationFilter(authManager, customAuthenticationEntryPoint), classOf[BasicAuthenticationFilter])

    if (isKerberosEnabled) {
      val kerberos = new KerberosSPNEGOAuthenticationProvider(ldapConfig)
      http.addFilterBefore(
        kerberos.spnegoAuthenticationProcessingFilter,
        classOf[BasicAuthenticationFilter])
    }

    http.build()
  }

  private def customAuthenticationEntryPoint: AuthenticationEntryPoint =
    (request: HttpServletRequest, response: HttpServletResponse, authException: AuthenticationException) => {
      if (isKerberosEnabled) {
        response.addHeader("WWW-Authenticate", """Basic realm="Realm"""")
        response.addHeader("WWW-Authenticate", "Negotiate")
      }
      authException match {
        case LdapConnectionException(msg, _) =>
          response.setStatus(HttpServletResponse.SC_GATEWAY_TIMEOUT);
          response.setContentType("application/json");
          response.getWriter.write(s"""{"error": "LDAP connection failed: $msg"}""");

        case _ =>
          response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      }
    }

}
