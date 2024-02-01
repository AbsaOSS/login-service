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

package za.co.absa.loginsvc.rest.config.auth

import za.co.absa.loginsvc.rest.config.validation.ConfigValidationResult.{ConfigValidationError, ConfigValidationSuccess}
import za.co.absa.loginsvc.rest.config.validation.{ConfigValidatable, ConfigValidationException, ConfigValidationResult}

case class UsersConfig(knownUsers: Array[UserConfig], order: Int)
  extends ConfigValidatable with DynamicAuthOrder {

  lazy val knownUsersMap: Map[String, UserConfig] = knownUsers
    .map { entry => (entry.username, entry) }
    .toMap

  def throwErrors(): Unit =
    this.validate().throwOnErrors()

  // todo validation is done using a custom trait/method -- Issue #24 validation
  // Until is resolved https://github.com/spring-projects/spring-boot/issues/33669
  override def validate(): ConfigValidationResult = {

    if(order > 0)
    {
      Option(knownUsers).map { existingKnownUsers =>

        val kuDuplicatesResult = {
          val groupedByUsers = existingKnownUsers.groupBy(_.username)
          if (groupedByUsers.size < existingKnownUsers.length) {
            val duplicates = groupedByUsers.filter { case (username, configs) => configs.length > 1 }.keys
            ConfigValidationError(ConfigValidationException(s"knownUsers contain duplicates, duplicated usernames: ${duplicates.mkString(", ")}"))
          } else ConfigValidationSuccess
        }

        val usersResult = existingKnownUsers.map(_.validate()).toList

        (kuDuplicatesResult :: usersResult)
          .foldLeft[ConfigValidationResult](ConfigValidationSuccess)(ConfigValidationResult.merge)

      }.getOrElse(ConfigValidationError(ConfigValidationException("knownUsers is missing")))
    }
    else ConfigValidationSuccess
  }
}

case class UserConfig(username: String,
                       password: String,
                       groups: Array[String],
                       attributes: Option[Map[String, String]]
                     ) extends ConfigValidatable {

  override def toString: String = {
    s"UserConfig($username, $password, ${Option(groups).map(_.toList)}, ${attributes.getOrElse(Map.empty).mkString(",")}))}"
  }

  override def validate(): ConfigValidationResult = {
    val results = Seq(
      Option(username)
        .map(_ => ConfigValidationSuccess)
        .getOrElse(ConfigValidationError(ConfigValidationException("username is empty"))),

      Option(password)
        .map(_ => ConfigValidationSuccess)
        .getOrElse(ConfigValidationError(ConfigValidationException("password is empty"))),

      Option(groups)
        .map(_ => ConfigValidationSuccess)
        .getOrElse(ConfigValidationError(ConfigValidationException("groups are missing (empty groups are allowed)!")))

    )

    results.foldLeft[ConfigValidationResult](ConfigValidationSuccess)(ConfigValidationResult.merge)
  }
}
