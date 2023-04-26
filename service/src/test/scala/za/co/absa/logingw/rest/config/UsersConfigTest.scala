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

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import za.co.absa.logingw.rest.config.validation.ConfigValidationException
import za.co.absa.logingw.rest.config.validation.ConfigValidationResult.{ConfigValidationError, ConfigValidationSuccess}

class UsersConfigTest extends AnyFlatSpec with Matchers {

  val userCfg = UserConfig("user1", "password1", "mail@here.tld", Array("group1", "group2"))

  "UserConfig" should "validate expected filled content" in {
    userCfg.validate() shouldBe ConfigValidationSuccess
  }

  it should "fail on missing user/pwd/groups" in {
    userCfg.copy(username = null).validate() shouldBe
      ConfigValidationError(ConfigValidationException("username is empty"))

    userCfg.copy(password = null).validate() shouldBe
      ConfigValidationError(ConfigValidationException("password is empty"))

    val groupsValidation = userCfg.copy(groups = null).validate()
    groupsValidation shouldBe a[ConfigValidationError]
    groupsValidation.getErrors should have size 1
    groupsValidation.getErrors.head.msg should include("groups are missing") // there is other info

  }

  it should "succeed empty groups" in {
    userCfg.copy(groups = Array.empty).validate() shouldBe ConfigValidationSuccess
  }

  it should "succeed missing email (it is optional)" in {
    userCfg.copy(email = null).validate() shouldBe ConfigValidationSuccess
  }

  it should "throw validation exception with .failOnValidationError wrapper" in {
    intercept[ConfigValidationException] {
      userCfg.copy(password = null).failOnValidationError()
    }.msg should include("password is empty")
  }

  val usersCfg = UsersConfig(knownUsers = Array(userCfg))
  "UsersConfig" should "validate ok expected filled content" in {
    usersCfg.validate() shouldBe ConfigValidationSuccess
  }

  it should "fail on missing knownUsers" in {
    UsersConfig(knownUsers = null).validate() shouldBe
      ConfigValidationError(ConfigValidationException("knownUsers is missing"))
  }

  it should "succeed with empty users" in {
    UsersConfig(knownUsers = Array()).validate() shouldBe ConfigValidationSuccess
  }

  it should "fail on duplicate knownUsers" in {
    val duplicateValidationResult = UsersConfig(knownUsers = Array(
      UserConfig("sameUser", "password1", "mail@here.tld", Array("group1", "group2")),
      UserConfig("sameUser", "password2", "anotherMail@here.tld", Array()),

      UserConfig("sameUser2", "passwordX", "abc@def", Array()),
      UserConfig("sameUser2", "passwordA", null, Array()),

      UserConfig("okUser", "passwordO", "ooo@", Array())
    )).validate()

    duplicateValidationResult shouldBe a[ConfigValidationError]
    duplicateValidationResult.getErrors should have size 1

    val errorMsg = duplicateValidationResult.getErrors.head.msg
    errorMsg should include("knownUsers contain duplicates")
    errorMsg should include("duplicated usernames: sameUser, sameUser2")
  }

  it should "fail multiple errors" in {
    val multiErrorsResult = UsersConfig(knownUsers = Array(
      UserConfig("sameUser", "password1", "mail@here.tld", Array("group1", "group2")),
      UserConfig("sameUser", "password2", "anotherMail@here.tld", Array()),

      UserConfig("userNoPass", null, "abc@def", Array()),
      UserConfig("noMailIsFine", "password2", null, Array()),
      UserConfig("userNoMissingGroups", "passwordO", "ooo@", null)
    )).validate()

    multiErrorsResult shouldBe a[ConfigValidationError]
    multiErrorsResult.getErrors should have size 3

    val errorMsgs = multiErrorsResult.getErrors.map(_.msg)
    errorMsgs should contain theSameElementsAs Seq(
      "knownUsers contain duplicates, duplicated usernames: sameUser",
      "password is empty",
      "groups are missing (empty groups are allowed)!"
    )
  }

}
