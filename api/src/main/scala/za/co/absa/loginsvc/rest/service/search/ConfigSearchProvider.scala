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
import za.co.absa.loginsvc.rest.config.auth.UsersConfig

class ConfigSearchProvider(usersConfig: UsersConfig)
  extends AuthSearchProvider {

  private val logger = LoggerFactory.getLogger(classOf[ConfigSearchProvider])
  def searchForUser(username: String): Option[User] = {
    logger.info(s"Searching for user in config: $username")
    usersConfig.knownUsersMap.get(username).flatMap { userConfig =>
      val optionalAttributes: Map[String, Option[AnyRef]] = userConfig.attributes.getOrElse(Map.empty).map {
        case (k, v) => (k, Some(v))
      }
      logger.info(s"User found in config: $username")
      Option(User(userConfig.username, userConfig.groups.toList, optionalAttributes))
    }
  }
}
