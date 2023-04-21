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

package za.co.absa.logingw.rest.provider

import org.springframework.security.authentication.{AuthenticationProvider, BadCredentialsException, UsernamePasswordAuthenticationToken}
import org.springframework.security.core.{Authentication, GrantedAuthority}
import za.co.absa.logingw.model.User


class DummyAuthenticationProvider extends AuthenticationProvider {

  private val validUsername: String = "TestUser"
  private val validPassword: String = "password123"

  override def authenticate(authentication: Authentication): Authentication = {
    import scala.collection.JavaConverters._

    val username = authentication.getName
    val password = authentication.getCredentials.toString

    if(username == validUsername && password == validPassword) {
      val principal = User(username, Some("test@abs.com"), Seq.empty)
      new UsernamePasswordAuthenticationToken(principal, password, Seq.empty[GrantedAuthority].asJava)
    } else throw new BadCredentialsException("Bad credentials provided.")

  }

  override def supports(authentication: Class[_]): Boolean =
    authentication == classOf[UsernamePasswordAuthenticationToken]

}
