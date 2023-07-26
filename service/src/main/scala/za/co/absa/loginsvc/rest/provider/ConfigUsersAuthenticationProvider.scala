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

package za.co.absa.loginsvc.rest.provider

import org.slf4j.LoggerFactory
import org.springframework.security.authentication.{AuthenticationProvider, BadCredentialsException, UsernamePasswordAuthenticationToken}
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import za.co.absa.loginsvc.model.User
import za.co.absa.loginsvc.rest.config.auth.UsersConfig

class ConfigUsersAuthenticationProvider(usersConfig: UsersConfig) extends AuthenticationProvider {

  private val logger = LoggerFactory.getLogger(classOf[ConfigUsersAuthenticationProvider])
  logger.debug(s"ConfigUsersAuthenticationProvider init")

  override def authenticate(authentication: Authentication): Authentication = {
    import scala.collection.JavaConverters._

    val username = authentication.getName
    val password = authentication.getCredentials.toString

    lazy val badCreds = new BadCredentialsException("Bad credentials provided.") // deliberately do not provide more info

    usersConfig.knownUsersMap.get(username).map { usersConfig =>
      if (usersConfig.password == password) {
        logger.info(s"user login: $username - ok")
        val principal = User(username, usersConfig.email, usersConfig.groups.flatten.toList)
        new UsernamePasswordAuthenticationToken(principal, password,
          usersConfig.groups.flatten.map(new SimpleGrantedAuthority(_)).toList.asJava)
      } else {
        logger.error(s"user login: $username - bad pwd")
        throw badCreds
      }
    }.getOrElse{
      logger.error(s"user login: $username - user not found")
      throw badCreds
    }
  }

  override def supports(authentication: Class[_]): Boolean =
    authentication == classOf[UsernamePasswordAuthenticationToken]

}
