package za.co.absa.loginsvc.model

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class UserTest extends AnyFlatSpec with Matchers {

  val testUser = User("testUser", None, None, groups = Seq(
    "blue-123",
    "blue-256",
    "red-ABC",
    "reddish-DEF",
    "black",
    "black-and-white"
  ))

  "User" should "filterGroups by prefixes" in {
    testUser.filterGroupsByPrefixes(Set("red", "black", "yellow")) shouldBe
      testUser.copy(groups = Seq("red-ABC", "reddish-DEF", "black","black-and-white"))
  }

}
