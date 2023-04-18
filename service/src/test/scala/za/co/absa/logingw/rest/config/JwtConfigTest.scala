package za.co.absa.logingw.rest.config

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class JwtConfigTest extends AnyFlatSpec with Matchers {

  val jwtConfig = JwtConfig("RS256", 2)

  "JwtConfig" should "validate expected content" in {
    jwtConfig.validate()
  }

  it should "fail on invalid algorithm" in {
    intercept[IllegalArgumentException] {
      jwtConfig.copy(algName = "ABC").validate()
    }.getMessage should include("No enum constant io.jsonwebtoken.SignatureAlgorithm.ABC")
  }

  it should "fail on non-negative expTime" in {
    intercept[ConfigValidationException] {
      jwtConfig.copy(expTime = -7).validate()
    }.getMessage should include("expTime must be positive")
  }

}
