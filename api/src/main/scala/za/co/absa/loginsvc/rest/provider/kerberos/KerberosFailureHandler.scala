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

package za.co.absa.loginsvc.rest.provider.kerberos

import org.slf4j.LoggerFactory
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.authentication.AuthenticationFailureHandler
import za.co.absa.loginsvc.rest.provider.ad.ldap.LdapConnectionException
import za.co.absa.loginsvc.rest.service.search.LdapUserRepository

import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

class KerberosFailureHandler extends AuthenticationFailureHandler {

  private val logger = LoggerFactory.getLogger(classOf[KerberosFailureHandler])

  override def onAuthenticationFailure(
    request: HttpServletRequest,
    response: HttpServletResponse,
    exception: AuthenticationException): Unit = {
    response.addHeader("WWW-Authenticate", """Basic realm="Realm"""")
    response.addHeader("WWW-Authenticate", "Negotiate")
    response.setContentType("application/json")
    response.getWriter.write(s"""{"error": "Error handled by handler ${exception.getClass.getName}"}""")
    response.getWriter.write(s"""{"error": "Inner Error handled by handler ${exception.getCause.getClass.getName}"}""")

    exception.getCause match {
      case LdapConnectionException(msg, _) =>
        response.setStatus(HttpServletResponse.SC_GATEWAY_TIMEOUT)
        response.setContentType("application/json")
        response.getWriter.write(s"""{"error": "LDAP connection failed: $msg"}""")
      case _ =>
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED)
        response.setContentType("application/json")
        response.getWriter.write(s"""{"error": "User unauthorized"}""")
    }
  }
}
