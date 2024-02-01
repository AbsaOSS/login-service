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

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import za.co.absa.loginsvc.rest.config.validation.ConfigValidationException
import za.co.absa.loginsvc.rest.config.validation.ConfigValidationResult.{ConfigValidationError, ConfigValidationSuccess}

class UsersConfigTest extends AnyFlatSpec with Matchers {

  private val userCfg = UserConfig("user1", "password1", Array("group1", "group2"), Some(Map("mail" -> "mail@here.tld", "displayname" -> "Fake Name")))

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
    groupsValidation.errors should have size 1
    groupsValidation.errors.head.msg should include("groups are missing") // there is other info

  }

  it should "succeed empty groups" in {
    userCfg.copy(groups = Array.empty).validate() shouldBe ConfigValidationSuccess
  }

  it should "succeed missing attributes (it is optional)" in {
    userCfg.copy(attributes = Option(Map.empty)).validate() shouldBe ConfigValidationSuccess
  }

  private val usersCfg = UsersConfig(knownUsers = Array(userCfg), 1)
  "UsersConfig" should "validate ok expected filled content" in {
    usersCfg.validate() shouldBe ConfigValidationSuccess
  }

  it should "fail on missing knownUsers" in {
    UsersConfig(knownUsers = null, 1).validate() shouldBe
      ConfigValidationError(ConfigValidationException("knownUsers is missing"))
  }

  it should "succeed with empty users" in {
    UsersConfig(knownUsers = Array(), 1).validate() shouldBe ConfigValidationSuccess
  }

  it should "fail on duplicate knownUsers" in {
    val duplicateValidationResult = UsersConfig(knownUsers = Array(
      UserConfig("sameUser", "password1", Array("group1", "group2"), Option(Map("mail" -> "mail@here.tld", "displayname" -> "Fake1"))),
      UserConfig("sameUser", "password2", Array(), Option(Map("mail" -> "anotherMail@here.tld", "displayname" -> "Fake2"))),

      UserConfig("sameUser2", "passwordX", Array(), Option(Map("mail" -> "abc@def", "displayname" -> "Fake1"))),
      UserConfig("sameUser2", "passwordA", Array(), Option(Map.empty)),

      UserConfig("okUser", "passwordO", Array(), Option(Map("mail" -> "ooo@", "displayname" -> "Fake1")))
    ), 1).validate()

    duplicateValidationResult shouldBe a[ConfigValidationError]
    duplicateValidationResult.errors should have size 1

    val errorMsg = duplicateValidationResult.errors.head.msg
    errorMsg should include("knownUsers contain duplicates")
    errorMsg should include("duplicated usernames: sameUser, sameUser2")
  }

  it should "fail multiple errors" in {
    val multiErrorsResult = UsersConfig(knownUsers = Array(
      UserConfig("sameUser", "password1", Array("group1", "group2"), Option(Map("mail" -> "mail@here.tld", "displayname" -> "Fake1"))),
      UserConfig("sameUser", "password2", Array(), Option(Map("mail" -> "anotherMail@here.tld", "displayname" -> "Fake2"))),

      UserConfig("userNoPass", null, Array(), Option(Map("mail" -> "abc@def", "displayname" -> "FakeName"))),
      UserConfig("noMailIsFine", "password2", Array(), Option(Map.empty)),
      UserConfig("userNoMissingGroups", "passwordO", null, Option(Map("mail" -> "ooo@", "displayname" -> "Fake2")))
      ), 1).validate()

    multiErrorsResult shouldBe a[ConfigValidationError]
    multiErrorsResult.errors should have size 3

    val errorMsgs = multiErrorsResult.errors.map(_.msg)
    errorMsgs should contain theSameElementsAs Seq(
      "knownUsers contain duplicates, duplicated usernames: sameUser",
      "password is empty",
      "groups are missing (empty groups are allowed)!"
    )
  }

  it should "pass validation if disabled despite missing values" in {
    UsersConfig(knownUsers = null, 0).validate() shouldBe ConfigValidationSuccess
  }
}
