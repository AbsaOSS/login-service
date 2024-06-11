package za.co.absa.loginsvc.rest.provider.kerberos

import org.springframework.ldap.core.DirContextOperations
import org.springframework.ldap.core.support.BaseLdapPathContextSource
import org.springframework.security.ldap.search.FilterBasedLdapUserSearch

class KerberosLdapUserSearch (searchBase: String, searchFilter: String, contextSource: BaseLdapPathContextSource)
  extends FilterBasedLdapUserSearch(searchBase, searchFilter, contextSource) {

  override def searchForUser(username: String): DirContextOperations = {
    val user = if (username.contains("@")) {
      username.split("@").head
    } else {
      username
    }
    super.searchForUser(user)
  }
}
