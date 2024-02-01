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

package za.co.absa.loginclient.authorization

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.springframework.security.oauth2.jwt.{Jwt, JwtDecoder}
import za.co.absa.loginclient.exceptions.LsJwtException

import java.time.Instant
import java.util.Base64

object ClaimsParserTest {
  val publicKeyString: String = Base64.getEncoder.encodeToString(FakeTokens.keys.getPublic.getEncoded)
  val decoder: JwtDecoder = JwtDecoderProvider.getDecoderFromPublicKeyString(publicKeyString)

  val missingTokenTypeJwt: Jwt = decoder.decode(FakeTokens.missingTokenTypeToken)
  val missingAllClaimsButSubjectJwt: Jwt = decoder.decode(FakeTokens.missingAllClaimsButSubjectToken)

}


class AccessClaimsParserTest extends AnyFlatSpec with Matchers {

  private val accessJwt = ClaimsParserTest.decoder.decode(FakeTokens.validAccessToken)
  import ClaimsParserTest._

  "Access token" should "return a subject that equals 'testUser'" in {
    val subject = AccessTokenClaimsParser.getSubject(accessJwt)
    assert(subject.equals(FakeTokens.subject))
  }

  it should "return a list of groups that contain 'group1', 'group2')" in {
    val groups = AccessTokenClaimsParser.getGroups(accessJwt)
    assert(groups == List("group1", "group2"))
  }

  it should "return an empty groups even if groups claim is not present at all" in {
    val groups = AccessTokenClaimsParser.getGroups(missingAllClaimsButSubjectJwt) // does not throw
    assert(groups == List.empty)
  }

  it should "return a expiration date that should be within an hour of testing" in {
    val exp = AccessTokenClaimsParser.getExpiration(accessJwt)
    val check = FakeTokens.validExpiration.toInstant
    assert(exp.equals(Instant.ofEpochSecond(check.getEpochSecond)))
  }

  it should "return an exception for missing expiration" in {
    val exception = the[LsJwtException] thrownBy {
      AccessTokenClaimsParser.getExpiration(missingAllClaimsButSubjectJwt)
    }
    exception.getMessage shouldBe "Expiration not found"
  }

  it should "return the set time of issue" in {
    val issueTime = AccessTokenClaimsParser.getIssueTime(accessJwt)
    val check = FakeTokens.issuedAt.toInstant
    assert(issueTime.equals(Instant.ofEpochSecond(check.getEpochSecond)))
  }

  it should "return an exception for missing issuedAt" in {
    val exception = the[LsJwtException] thrownBy {
      AccessTokenClaimsParser.getIssueTime(missingAllClaimsButSubjectJwt)
    }
    exception.getMessage shouldBe "Issue time not found"
  }

  it should "return the email address" in {
    val email = AccessTokenClaimsParser.getOptionalClaims(accessJwt)
    assert(email("email").toString.equals(FakeTokens.email))
  }

  it should "return an empty email if email claim is not present at all" in {
    val email = AccessTokenClaimsParser.getOptionalClaims(missingAllClaimsButSubjectJwt) // does not throw
    email shouldBe Map.empty
  }

  it should "return the display name" in {
    val displayName = AccessTokenClaimsParser.getOptionalClaims(accessJwt)
    assert(displayName("displayname").toString.equals(FakeTokens.displayName))
  }

  it should "return an empty display name if displayname claim is not present at all" in {
    val dn = AccessTokenClaimsParser.getOptionalClaims(missingAllClaimsButSubjectJwt) // does not throw
    dn shouldBe Map.empty
  }

  it should "return the token type" in {
    val tokenType = AccessTokenClaimsParser.getTokenType(accessJwt)
    assert(tokenType.equals("access"))
  }

  it should "return an exception for missing token type" in {
    val exception = the[LsJwtException] thrownBy {
      AccessTokenClaimsParser.getTokenType(missingTokenTypeJwt)
    }
    exception.getMessage shouldBe "Token type not found"
  }

  it should "check access token type" in {
    assert(AccessTokenClaimsParser.isAccessTokenType(accessJwt))
  }

  it should "return an exception for missing token type 2" in {
    val exception = the[LsJwtException] thrownBy {
      AccessTokenClaimsParser.isAccessTokenType(missingTokenTypeJwt)
    }
    exception.getMessage shouldBe "Token type not found"
  }
}
class RefreshClaimsParserTest extends AnyFlatSpec with Matchers {
  
  private val refreshJwt = ClaimsParserTest.decoder.decode(FakeTokens.validRefreshToken)
  import ClaimsParserTest._

  "Refresh Token" should "return a subject that equals 'testUser'" in {
    val sub = RefreshTokenClaimsParser.getSubject(refreshJwt)
    assert(sub.equals(FakeTokens.subject))
  }

  it should "return a expiration date that should be within an hour of testing" in {
    val exp = RefreshTokenClaimsParser.getExpiration(refreshJwt)
    val check = FakeTokens.validExpiration.toInstant
    assert(exp.equals(Instant.ofEpochSecond(check.getEpochSecond)))
  }

  it should "return an exception for missing expiration" in {
    val exception = the[LsJwtException] thrownBy {
      RefreshTokenClaimsParser.getExpiration(missingAllClaimsButSubjectJwt)
    }
    exception.getMessage shouldBe "Expiration not found"
  }

  it should "return the set time of issue" in {
    val issueTime = RefreshTokenClaimsParser.getIssueTime(refreshJwt)
    val check = FakeTokens.issuedAt.toInstant
    assert(issueTime.equals(Instant.ofEpochSecond(check.getEpochSecond)))
  }

  it should "return an exception for missing issuedAt" in {
    val exception = the[LsJwtException] thrownBy {
      RefreshTokenClaimsParser.getIssueTime(missingAllClaimsButSubjectJwt)
    }
    exception.getMessage shouldBe "Issue time not found"
  }

  it should "return the token type" in {
    val tokenType = RefreshTokenClaimsParser.getTokenType(refreshJwt)
    assert(tokenType.equals("refresh"))
  }

  it should "return an exception for missing token type" in {
    val exception = the[LsJwtException] thrownBy {
      RefreshTokenClaimsParser.getTokenType(missingTokenTypeJwt)
    }
    exception.getMessage shouldBe "Token type not found"
  }

  it should "check refresh token type" in {
    assert(RefreshTokenClaimsParser.isRefreshTokenType(refreshJwt))
  }

  it should "return an exception for missing token type 2" in {
    val exception = the[LsJwtException] thrownBy {
      RefreshTokenClaimsParser.isRefreshTokenType(missingTokenTypeJwt)
    }
    exception.getMessage shouldBe "Token type not found"
  }
}
