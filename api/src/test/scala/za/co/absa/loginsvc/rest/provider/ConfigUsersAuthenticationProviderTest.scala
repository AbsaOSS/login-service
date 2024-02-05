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

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.springframework.security.authentication.{BadCredentialsException, UsernamePasswordAuthenticationToken}
import org.springframework.security.core.GrantedAuthority
import za.co.absa.loginsvc.rest.config.auth.{UserConfig, UsersConfig}
import za.co.absa.loginsvc.rest.provider.ConfigUsersAuthenticationProviderTest.simpleCredentialsToSpringAuth

class ConfigUsersAuthenticationProviderTest extends AnyFlatSpec with Matchers {

  // testing config we are running against
  val testConfig: UsersConfig = UsersConfig(Array(
    UserConfig("testuser", "testpassword", Array(), Option(Map("mail" -> "testuser@example.com", "displayname" -> "Test User")))
  ), 1)
  val authProvider = new ConfigUsersAuthenticationProvider(testConfig)

  "ConfigUsersAuthenticationProvider" should "authenticate authenticate with correct credentials" in {
    val testedAuth = simpleCredentialsToSpringAuth("testuser", "testpassword")
    authProvider.authenticate(testedAuth)
  }

  it should "authenticate fail authentication with existing user, but bad password" in {
    intercept[BadCredentialsException] {
      val testedAuth = simpleCredentialsToSpringAuth("testuser", "badpassword")
      authProvider.authenticate(testedAuth)
    }.getMessage should include("Bad credentials provided.")
  }

  it should "authenticate fail authentication with non-existing user" in {
    intercept[BadCredentialsException] {
      val testedAuth = simpleCredentialsToSpringAuth("baduser", "aPassword")
      authProvider.authenticate(testedAuth)
    }.getMessage should include("Bad credentials provided.")
  }

}

object ConfigUsersAuthenticationProviderTest {

  def simpleCredentialsToSpringAuth(username: String, password: String): UsernamePasswordAuthenticationToken = {

    import org.springframework.security.core.userdetails.{User => SpringUser}

    val testedUser = new SpringUser(username, "notchecked@example.com", new java.util.ArrayList[GrantedAuthority]())
    new UsernamePasswordAuthenticationToken(testedUser, password, new java.util.ArrayList[GrantedAuthority]())
  }
}
