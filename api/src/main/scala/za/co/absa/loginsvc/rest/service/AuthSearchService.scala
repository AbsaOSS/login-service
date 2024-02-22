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

package za.co.absa.loginsvc.rest.service

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import za.co.absa.loginsvc.model.User
import za.co.absa.loginsvc.rest.config.auth.{ActiveDirectoryLDAPConfig, UsersConfig}
import za.co.absa.loginsvc.rest.config.provider.AuthConfigProvider

import java.util
import javax.naming.Context
import javax.naming.directory.{Attributes, DirContext, SearchControls, SearchResult}
import javax.naming.ldap.{Control, InitialLdapContext, PagedResultsControl}
import scala.collection.JavaConverters.enumerationAsScalaIteratorConverter

@Service
class AuthSearchService @Autowired()(authConfigProvider: AuthConfigProvider) {

  private val logger = LoggerFactory.getLogger(classOf[AuthSearchService])

  private val usersConfig = authConfigProvider.getUsersConfig
  private val adLDAPConfig = authConfigProvider.getLdapConfig

  private val configs = Array(usersConfig, adLDAPConfig).filter(_.order != 0).sortBy(_.order)

  def searchUser(username: String): User = {
    configs.foreach {
      case u: UsersConfig =>
        val result = searchUsersConfig(username)
        if (result.isDefined) return result.get
      case l: ActiveDirectoryLDAPConfig =>
        val result = searchLDAP(username)
        if (result.isDefined) return result.get
      case _ => //ignore for now
    }
    throw new NoSuchElementException(s"Value not found in any object.")
  }

  private def searchUsersConfig(username: String): Option[User] = {
    usersConfig.knownUsersMap.get(username).flatMap { userConfig =>
      val optionalAttributes: Map[String, Option[AnyRef]] = userConfig.attributes.getOrElse(Map.empty).map {
        case (k, v) => (k, Some(v))
      }
      Some(User(username, userConfig.groups.toList, optionalAttributes))
    }
  }

  private def searchLDAP(username: String): Option[User] = {
    val serviceAccount = adLDAPConfig.serviceAccount
    val context = getDirContext(serviceAccount.username, serviceAccount.password)
      try {
        val users = context
          .search(adLDAPConfig.domain.split("\\.").map(part => s"dc=$part").mkString(","),
            adLDAPConfig.searchFilter.replace("{1}", username),
            getSimpleSearchControls)
          .asScala.filter(filterSearchResults).map(resultToUserEntry).toList

        if (users.nonEmpty) {
          println("User found in LDAP")
          Option(users.head)
        }
        else
          throw new Exception(s"$username not found in LDAP")
      } finally {
        context.close()
      }
  }

  private def getDirContext(principal: String, credential: String): DirContext = {
    logger.info(String.format("principal: %s", principal))
    val env: util.Hashtable[String, String] = new util.Hashtable[String, String]
    env.put(Context.PROVIDER_URL, adLDAPConfig.url)
    env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory")
    env.put(Context.SECURITY_AUTHENTICATION, "simple")
    env.put(Context.SECURITY_PRINCIPAL, principal)
    env.put(Context.SECURITY_CREDENTIALS, credential)

    new InitialLdapContext(env, Array[Control](new PagedResultsControl(1000, Control.CRITICAL)))
  }

  private def getSimpleSearchControls: SearchControls = {
    val searchControls: SearchControls = new SearchControls
    searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE)
    searchControls.setTimeLimit(30000)
    searchControls.setCountLimit(1000)
    searchControls
  }

  private def filterSearchResults(result: SearchResult): Boolean = {
    val attrs = result.getAttributes
    if (attrs == null) {
      logger.error("No Attributes")
      false
    } else if (attrs.get("sAMAccountName") == null) {
      logger.error("No Attribute sAMAccountName")
      val attrsAll = attrs.getAll
      while (attrsAll.hasMoreElements) {
        val attribute = attrsAll.next
        logger.warn(String.format("%s : %s", attribute.getID, attribute.get))
      }
      false
    } else true
  }

  private def resultToUserEntry(result: SearchResult): User = {
    val attrs: Attributes = result.getAttributes
    val optionalAttr= adLDAPConfig.attributes.getOrElse(Map.empty)

    val username = attrs.get("sAMAccountName").get.toString
    val groups = attrs.get("memberOf").getAll.asScala.map(group => {
      group.toString.substring(3, group.toString.indexOf(","))
    }).toSeq
    val extraAttributes = optionalAttr.map { case (fieldName, claimName) =>
      val value = Option(attrs.get(fieldName)).map(_.get())
      claimName -> value
    }
    User(username, groups, extraAttributes)
  }
}
