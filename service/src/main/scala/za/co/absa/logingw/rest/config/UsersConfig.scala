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

package za.co.absa.logingw.rest.config

import org.springframework.boot.context.properties.{ConfigurationProperties, ConstructorBinding}

import javax.annotation.PostConstruct

@ConstructorBinding
@ConfigurationProperties(prefix = "logingw.rest.users")
case class UsersConfig(
  knownUsers: Array[UserConfig],
) extends ConfigValidatable {
  lazy val knownUsersMap: Map[String, UserConfig] = knownUsers
    .map { entry => (entry.username, entry) }
    .toMap

  // todo validation is done using a custom trait/method -- Issue #24 validation
  // Until is resolved https://github.com/spring-projects/spring-boot/issues/33669
  override def validate(): Unit = {
    if (Option(knownUsers).isEmpty) {
      throw new ConfigValidationException("knownUsers is missing")
    }

    val groupedByUsers = knownUsers.groupBy(_.username)
    if (groupedByUsers.size < knownUsers.size) {
      val duplicates = groupedByUsers.filter { case (username, configs) => configs.length > 1 }.map(_._1)
      throw new ConfigValidationException(s"knownUsers contain duplicates, duplicated usernames: ${duplicates.mkString(", ")}")
    }

    knownUsers.foreach(_.validate())
  }

  @PostConstruct
  def init() {
    this.validate()
  }
}

@ConstructorBinding
case class UserConfig(
  username: String,
  password: String,
  email: String,
  groups: Array[String]
) extends ConfigValidatable {

  override def toString(): String = {
    s"UserConfig($username, $password, ${Option(email)}, ${Option(groups).map(_.toList)})"
  }

  override def validate() = {
    if (Option(username).isEmpty) {
      throw new ConfigValidationException("username is empty")
    }

    if (Option(password).isEmpty) {
      throw new ConfigValidationException("password is empty")
    }

    if (Option(email).isEmpty) {
      throw new ConfigValidationException("email is empty")
    }

    if (Option(groups).isEmpty) {
      throw new ConfigValidationException("groups are missing (empty groups are allowed)!")
    }

  }
}
