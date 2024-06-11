package za.co.absa.loginsvc.rest.provider.kerberos

import org.springframework.security.ldap.DefaultSpringSecurityContextSource

import java.security.PrivilegedAction
import javax.naming.Context
import javax.naming.directory.DirContext
import javax.security.auth.Subject

class RestApiKerberosLdapContextSource (url: String, subject: Subject) extends DefaultSpringSecurityContextSource(url) {
  override def getDirContextInstance(environment: java.util.Hashtable[String, Object]): DirContext = {

    environment.put(Context.SECURITY_AUTHENTICATION, "GSSAPI")
    val sup = super.getDirContextInstance _

    logger.debug(s"Trying to authenticate to LDAP as ${subject.getPrincipals}")
    Subject.doAs(subject, new PrivilegedAction[DirContext]() {
      override def run(): DirContext = {
        sup(environment)
      }
    })
  }
}
