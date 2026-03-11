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

package za.co.absa.loginsvc.rest.provider.entra

import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter
import za.co.absa.loginsvc.model.User

import javax.servlet.{FilterChain, ServletRequest, ServletResponse}
import javax.servlet.http.{HttpServletRequest, HttpServletResponse}
import scala.collection.JavaConverters._
import scala.util.{Failure, Success}

/**
 * Spring Security filter that intercepts requests carrying an MS Entra Bearer token.
 *
 * When an `Authorization: Bearer <token>` header is present and no authentication
 * is already established, delegates to [[MsEntraTokenValidator]] to validate the token
 * and populate the [[SecurityContextHolder]].
 *
 * On invalid tokens the request is rejected with HTTP 401.
 * On missing Bearer header the filter passes through, allowing other filters (e.g.
 * BasicAuth) to handle authentication.
 */
class MsEntraBearerTokenFilter(validator: MsEntraTokenValidator) extends OncePerRequestFilter {

  private val log = LoggerFactory.getLogger(classOf[MsEntraBearerTokenFilter])

  private val BearerPrefix = "Bearer "

  override def doFilterInternal(
    request: HttpServletRequest,
    response: HttpServletResponse,
    filterChain: FilterChain
  ): Unit = {
    val authHeader = Option(request.getHeader("Authorization"))

    authHeader match {
      case Some(header) if header.startsWith(BearerPrefix) =>
        // Only process if SecurityContext is not already populated
        if (SecurityContextHolder.getContext.getAuthentication != null) {
          filterChain.doFilter(request, response)
        } else {
          val rawToken = header.substring(BearerPrefix.length).trim
          validator.validate(rawToken) match {
            case Success(user) =>
              log.info(s"Entra-based: Login of user ${user.name} - ok")
              setAuthentication(user)
              filterChain.doFilter(request, response)

            case Failure(ex) =>
              log.warn(s"Entra Bearer token rejected: ${ex.getMessage}")
              SecurityContextHolder.clearContext()
              response.setStatus(HttpServletResponse.SC_UNAUTHORIZED)
              response.setContentType("application/json")
              response.getWriter.write(s"""{"error": "Invalid or expired Entra token"}""")
          }
        }

      case _ =>
        // No Bearer header — pass through to allow other auth mechanisms
        filterChain.doFilter(request, response)
    }
  }

  private def setAuthentication(user: User): Unit = {
    val authorities = user.groups.map(new SimpleGrantedAuthority(_)).asJava
    val authentication = new UsernamePasswordAuthenticationToken(user, null, authorities)
    SecurityContextHolder.getContext.setAuthentication(authentication)
  }
}
