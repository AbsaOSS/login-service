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
  enabled: Boolean,
  knownUsers: Array[UserConfig],
) extends ConfigValidatable {
  // todo validation of duplicates, etc -- Issue #24 validation
  lazy val knownUsersMap: Map[String, UserConfig] = knownUsers
    .map { entry => (entry.username, entry) }
    .toMap

  override def validate(): Unit = {
    if (Option(enabled).isEmpty) {
      throw new ConfigValidationException("enabled is empty")
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
  email: String, // todo sanitize nulls/values
  groups: Array[String]
) extends ConfigValidatable {

  override def toString(): String = {
    s"UserConfig($username, $password, ${Option(email)}, ${Option(groups).map(_.toList)})"
  }

  override def validate() = {
    // Until is resolved https://github.com/spring-projects/spring-boot/issues/33669
    if (Option(username).isEmpty) {
      throw new ConfigValidationException("Username is empty")
    }

    if (Option(password).isEmpty) {
      throw new ConfigValidationException("Password is empty")
    }

    if (Option(email).isEmpty) {
      throw new ConfigValidationException("Email is empty")
    }

    if (Option(groups).isEmpty) {
      throw new ConfigValidationException("groups are missing (empty groups are allowed)!")
    }

  }
}
