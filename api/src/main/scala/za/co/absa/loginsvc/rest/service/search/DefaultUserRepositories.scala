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

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import za.co.absa.loginsvc.rest.config.auth.{ActiveDirectoryLDAPConfig, ConfigOrdering, UsersConfig}
import za.co.absa.loginsvc.rest.config.provider.AuthConfigProvider
import za.co.absa.loginsvc.rest.config.validation.ConfigValidationException


@Component
class DefaultUserRepositories @Autowired()(authConfigProvider: AuthConfigProvider) extends UserRepositories {

  private val usersConfig: Option[ConfigOrdering] = authConfigProvider.getUsersConfig
  private val adLDAPConfig: Option[ConfigOrdering] = authConfigProvider.getLdapConfig

  private val configs: Seq[ConfigOrdering] = Seq(usersConfig, adLDAPConfig).flatten.filter(_.order != 0).sortBy(_.order)

  override val orderedProviders: Seq[UserRepository] = createUserRepositories(configs)

  if (orderedProviders.isEmpty)
    throw ConfigValidationException("No authentication method enabled in config")

  private def createUserRepositories(configs: Seq[ConfigOrdering]): Seq[UserRepository] = {
    Array.empty[UserRepository] ++ configs.filter(_.order > 0).sortBy(_.order)
      .map {
        case c: UsersConfig => new UsersFromConfigRepository(c)
        case c: ActiveDirectoryLDAPConfig => new LdapUserRepository(c)
        case other => throw new IllegalStateException(s"unsupported config $other")
      }
  }

}
