package za.co.absa.logingw.rest.config

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class UsersConfigTest extends AnyFlatSpec with Matchers {

  val userCfg = UserConfig("user1", "password1", "mail@here.tld", Array("group1", "group2"))

  "UserConfig" should "validate ok expected filled content" in {
    userCfg.validate()
  }

  it should "fail on missing user/pwd/mail/groups" in {
    intercept[ConfigValidationException] {
      userCfg.copy(username = null).validate()
    }.msg should include("username is empty")

    intercept[ConfigValidationException] {
      userCfg.copy(password = null).validate()
    }.msg should include("password is empty")

    intercept[ConfigValidationException] {
      userCfg.copy(email = null).validate()
    }.msg should include("email is empty")

    intercept[ConfigValidationException] {
      userCfg.copy(groups = null).validate()
    }.msg should include("groups are missing")
  }

  it should "validate ok empty groups" in {
    userCfg.copy(groups = Array.empty).validate()
  }

  val usersCfg = UsersConfig(knownUsers = Array(userCfg))

  "UsersConfig" should "validate ok expected filled content" in {
    usersCfg.validate()
  }

  it should "fail on missing knownUsers" in {
    intercept[ConfigValidationException] {
      usersCfg.copy(knownUsers = null).validate()
    }.msg should include("knownUsers is missing")

    // todo allow even empty users?

    // todo check duplicate username
  }

}
