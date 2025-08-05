package za.co.absa.loginsvc.rest.provider.kerberos

import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.authentication.AuthenticationFailureHandler
import za.co.absa.loginsvc.rest.provider.ad.ldap.LdapConnectionException

import javax.servlet.http.{HttpServletRequest, HttpServletResponse}

class KerberosFailureHandler extends AuthenticationFailureHandler {

  override def onAuthenticationFailure(
    request: HttpServletRequest,
    response: HttpServletResponse,
    exception: AuthenticationException): Unit = {
    response.addHeader("WWW-Authenticate", """Basic realm="Realm"""")
    response.addHeader("WWW-Authenticate", "Negotiate")
    exception.getCause match {
      case LdapConnectionException(msg, _) =>
        response.setStatus(HttpServletResponse.SC_GATEWAY_TIMEOUT);
        response.setContentType("application/json");
        response.getWriter.write(s"""{"error": "LDAP connection failed: $msg"}""");
      case _ =>
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter.write(s"""{"error": "User unauthorized"}""");
    }
  }
}
