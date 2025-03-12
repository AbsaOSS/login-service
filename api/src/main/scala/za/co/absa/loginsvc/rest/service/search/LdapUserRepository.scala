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

package za.co.absa.loginsvc.rest.service.search

import org.slf4j.LoggerFactory
import za.co.absa.loginsvc.model.User
import za.co.absa.loginsvc.rest.config.auth.ActiveDirectoryLDAPConfig

import java.util
import javax.naming.Context
import javax.naming.directory.{Attributes, DirContext, SearchControls, SearchResult}
import javax.naming.ldap.{Control, InitialLdapContext, PagedResultsControl}
import scala.collection.JavaConverters.enumerationAsScalaIteratorConverter
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}

class LdapUserRepository(activeDirectoryLDAPConfig: ActiveDirectoryLDAPConfig)
  extends UserRepository {

  private val logger = LoggerFactory.getLogger(classOf[LdapUserRepository])
  private val serviceAccount = activeDirectoryLDAPConfig.serviceAccount
  private val retryConfig = activeDirectoryLDAPConfig.LdapRetry
  private val context = getDirContext(serviceAccount.username, serviceAccount.password)

  def searchForUser(username: String): Option[User] = {
    logger.info(s"Searching for user in Ldap: $username")
    try {
      val users = retryConfig.fold(contextSearch(username))(x =>
        retrySearchAsync(x.attempts, x.delayMs, username))

      if (users.nonEmpty) {
        logger.info(s"User found in Ldap: $username")
        Option(users.head)
      }
      else {
        logger.info(s"User could not be found in Ldap: $username")
        None
      }
    } finally {
      context.close()
    }
  }

  private def getDirContext(principal: String, credential: String): DirContext = {
    logger.info(String.format("Service Account Principal: %s", principal))
    val env: util.Hashtable[String, String] = new util.Hashtable[String, String]
    env.put(Context.PROVIDER_URL, activeDirectoryLDAPConfig.url)
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
    val optionalAttr = activeDirectoryLDAPConfig.attributes.getOrElse(Map.empty)

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

  private def retrySearchAsync(attempts: Int, delayMs: Int, username: String): List[User] = {
    def attempt(n: Int): Future[List[User]] = Future {
      Try(contextSearch(username)) match {
        case Success(searchResults) => searchResults
        case Failure(ex) if n < attempts =>
          logger.error(s"AD authentication failed on attempt $n: ${ex.getMessage}. Retrying in ${delayMs}ms...")
          Thread.sleep(delayMs * n)
          Await.result(attempt(n + 1), Duration.Inf)
        case Failure(ex) =>
          logger.error(s"Search of user ${username}: ${ex.getMessage}", ex)
          ex.printStackTrace()
          throw ex
      }
    }
    Await.result(attempt(1), Duration.Inf)
  }

  private def contextSearch(username: String): List[User] = {
    context
      .search(activeDirectoryLDAPConfig.domain.split("\\.").map(part => s"dc=$part").mkString(","),
        activeDirectoryLDAPConfig.searchFilter.replace("{1}", username),
        getSimpleSearchControls)
      .asScala.filter(filterSearchResults).map(resultToUserEntry).toList
  }
}
