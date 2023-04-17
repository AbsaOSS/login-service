package za.co.absa.logingw.rest.config

import org.springframework.boot.context.properties.{ConfigurationProperties, ConstructorBinding}


/**
 * Configuration for AD LDAP(s) authentication provider.
 *
 * @param domain AD domain name, ex. "some.domain.com"
 * @param url URL to AD LDAP, ex. "ldaps://some.domain.com:636/"
 * @param searchFilter LDAP filter used when searching for groups, ex. "(samaccountname={1})"
 */
@ConstructorBinding
@ConfigurationProperties(prefix = "logingw.rest.auth.ad.ldap")
case class ActiveDirectoryLDAPConfig(domain: String, url: String, searchFilter: String)
