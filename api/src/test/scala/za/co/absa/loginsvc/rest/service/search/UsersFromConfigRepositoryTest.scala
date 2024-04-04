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

import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import za.co.absa.loginsvc.model.User
import za.co.absa.loginsvc.rest.config.auth.UsersConfig
import za.co.absa.loginsvc.rest.config.provider.ConfigProvider

class UsersFromConfigRepositoryTest extends AnyFlatSpec with BeforeAndAfterEach with Matchers {

  private val testConfig: ConfigProvider = new ConfigProvider("api/src/test/resources/application.yaml")
  private val usersConfig: UsersConfig = testConfig.getUsersConfig.get

  private val configSearchProvider: UsersFromConfigRepository = new UsersFromConfigRepository(usersConfig)

  private val user: User = User(
    name = "user2",
    groups = Seq("group2"),
    optionalAttributes = Map("mail" -> Some("user@two.org"))
  )

  it should "return a matching user" in {
    val result = configSearchProvider.searchForUser(user.name).get
    result.name shouldBe user.name
    result.optionalAttributes.get("mail") shouldBe user.optionalAttributes.get("mail")
    result.groups shouldBe user.groups
  }

  it should "return None if User does not exist" in {
    val result = configSearchProvider.searchForUser("FakeUser")
    result shouldBe None
  }


}
