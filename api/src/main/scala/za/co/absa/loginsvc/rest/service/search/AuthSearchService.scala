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
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import za.co.absa.loginsvc.model.User
import za.co.absa.loginsvc.rest.config.auth.{ActiveDirectoryLDAPConfig, DynamicAuthOrder, UsersConfig}
import za.co.absa.loginsvc.rest.config.provider.AuthConfigProvider
import za.co.absa.loginsvc.rest.config.validation.ConfigValidationException

@Service
class AuthSearchService @Autowired()(authConfigProvider: AuthConfigProvider) {

  private val logger = LoggerFactory.getLogger(classOf[AuthSearchService])

  private val usersConfig: Option[DynamicAuthOrder] = authConfigProvider.getUsersConfig
  private val adLDAPConfig: Option[DynamicAuthOrder] = authConfigProvider.getLdapConfig

  private val configs: Array[DynamicAuthOrder] = Array(usersConfig, adLDAPConfig).flatten.filter(_.order != 0).sortBy(_.order)
  private val orderedProviders = createProviders(configs)

  if (orderedProviders.isEmpty)
    throw ConfigValidationException("No authentication method enabled in config")

  def searchUser(username: String): User = {
    orderedProviders.foreach { provider =>
      val user = provider.searchForUser(username)
      if (user.isDefined) {
        return user.get
      }
    }
    throw new NoSuchElementException(s"Value not found in any object.")
  }

  private def createProviders(configs: Array[DynamicAuthOrder]): Array[AuthSearchProvider] = {
    Array.empty[AuthSearchProvider] ++ configs.filter(_.order > 0).sortBy(_.order)
      .map {
        case c: UsersConfig => new ConfigSearchProvider(c)
        case c: ActiveDirectoryLDAPConfig => new LdapSearchProvider(c)
        case other => throw new IllegalStateException(s"unsupported config $other")
      }
  }
}
