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

import org.scalamock.scalatest.MockFactory
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import za.co.absa.loginsvc.model.User

class UserSearchServiceTest extends AnyFlatSpec with BeforeAndAfterEach with Matchers with MockFactory {

  private case class Mocks(userRepositories: UserRepositories)

  private def createAuthServiceWithMocks: (UserSearchService, Mocks) = {
    val userRepositories = mock[UserRepositories]

    val userSearchService: UserSearchService = new UserSearchService(userRepositories)

    (userSearchService, Mocks(userRepositories))
  }

  private val user: User = User(
    name = "user2",
    groups = Seq("group2"),
    optionalAttributes = Map("mail" -> Some("user@two.org"))
  )

  def createDefinedUserRepository: UserRepository = {
    val definedUserRepository = mock[UserRepository]
    (definedUserRepository.searchForUser _)
      .expects("user2") // once set up it must be used
      .returns(Some(user))

    definedUserRepository
  }

  def createEmptyUserRepository: UserRepository = {
    val emptyUserRepository = mock[UserRepository]
    (emptyUserRepository.searchForUser _) // once set up it must be used
      .expects("user2")
      .returns(None)

    emptyUserRepository
  }

  private object testUserRepoException extends Throwable
  private val throwingUserRepository: UserRepository = new UserRepository {
    override def searchForUser(username: String): Option[User] = throw testUserRepoException
  }

  it should "return a matching user (single repo)" in {
    val (userSearchService, mocks) = createAuthServiceWithMocks
    (mocks.userRepositories.orderedProviders _)
      .expects()
      .returns(Seq(
        createDefinedUserRepository // proves that repo was used 1x
      ))

    val result = userSearchService.searchUser(user.name)

    result.name shouldBe user.name
    result.optionalAttributes.get("mail") shouldBe user.optionalAttributes.get("mail")
    result.groups shouldBe user.groups
  }

  it should "return user form the first successful service (multi repo)" in {
    val (userSearchService, mocks) = createAuthServiceWithMocks

    (mocks.userRepositories.orderedProviders _)
      .expects()
      .returns(Seq(
        createDefinedUserRepository,
        throwingUserRepository // proves short-circuiting: not reached
      ))

    val result = userSearchService.searchUser(user.name)
    result shouldBe user
  }

  it should "return user form the first successful service (multi repo) 2 " in {
    val (userSearchService, mocks) = createAuthServiceWithMocks

    (mocks.userRepositories.orderedProviders _)
      .expects()
      .returns(Seq(
        createEmptyUserRepository, // proves that repo was used 1x
        createDefinedUserRepository, // proves that repo was used 1x
        throwingUserRepository // proves short-circuiting: not reached
      ))

    val result = userSearchService.searchUser(user.name)
    result shouldBe user
  }

  it should "fail if user doesn't exist (single repo)" in {
    val (userSearchService, mocks) = createAuthServiceWithMocks
    (mocks.userRepositories.orderedProviders _)
      .expects()
      .returns(Seq(createEmptyUserRepository))

    an[NoSuchElementException] should be thrownBy {
      userSearchService.searchUser(user.name)
    }
  }

  it should "fail if user doesn't exist (multi repo)" in {
    val (userSearchService, mocks) = createAuthServiceWithMocks

    (mocks.userRepositories.orderedProviders _)
      .expects()
      .returns(Seq(
        createEmptyUserRepository,
        createEmptyUserRepository,
        createEmptyUserRepository
      ))

    an[NoSuchElementException] should be thrownBy {
      userSearchService.searchUser(user.name)
    }

  }

}
