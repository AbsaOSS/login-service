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

import java.time.Instant
import java.util.Base64

class ClaimsParserTest extends AnyFlatSpec with Matchers {

  private val publicKeyString = Base64.getEncoder.encodeToString(FakeTokens.keys.getPublic.getEncoded)
  private val decoder = JwtDecoderProvider.getDecoderFromPublicKeyString(publicKeyString)
  private val accessJwt = decoder.decode(FakeTokens.validAccessToken)
  private val refreshJwt = decoder.decode(FakeTokens.validRefreshToken)

  "Access token" should "return a subject that equals 'testUser'" in {
    val subject = AccessTokenClaimsParser.getSubject(accessJwt)
    assert(subject.equals(FakeTokens.subject))
  }

  "Access token" should "return a list of groups that contain 'group1', 'group2')" in {
    val groups = AccessTokenClaimsParser.getGroups(accessJwt)
    assert(groups == List("group1", "group2"))
  }

  "Access token" should "return a expiration date that should be within an hour of testing" in {
    val exp = AccessTokenClaimsParser.getExpiration(accessJwt)
    val check = FakeTokens.validExpiration.toInstant
    assert(exp.equals(Instant.ofEpochSecond(check.getEpochSecond)))
  }

  "Access token" should "return the set time of issue" in {
    val issueTime = AccessTokenClaimsParser.getIssueTime(accessJwt)
    val check = FakeTokens.issuedAt.toInstant
    assert(issueTime.equals(Instant.ofEpochSecond(check.getEpochSecond)))
  }

  "Access token" should "return the email address" in {
    val email = AccessTokenClaimsParser.getEmail(accessJwt)
    assert(email.get.equals(FakeTokens.email))
  }

  "Access token" should "return the display name" in {
    val displayName = AccessTokenClaimsParser.getDisplayName(accessJwt)
    assert(displayName.get.equals(FakeTokens.displayName))
  }

  "Access token" should "return the token type" in {
    val tokenType = AccessTokenClaimsParser.getTokenType(accessJwt)
    assert(tokenType.equals("access"))
  }

  "Refresh Token" should "return a subject that equals 'testUser'" in {
    val sub = RefreshTokenClaimsParser.getSubject(refreshJwt)
    assert(sub.equals(FakeTokens.subject))
  }

  "Refresh token" should "return a expiration date that should be within an hour of testing" in {
    val exp = RefreshTokenClaimsParser.getExpiration(refreshJwt)
    val check = FakeTokens.validExpiration.toInstant
    assert(exp.equals(Instant.ofEpochSecond(check.getEpochSecond)))
  }

  "Refresh token" should "return the set time of issue" in {
    val issueTime = RefreshTokenClaimsParser.getIssueTime(refreshJwt)
    val check = FakeTokens.issuedAt.toInstant
    assert(issueTime.equals(Instant.ofEpochSecond(check.getEpochSecond)))
  }

  "Refresh token" should "return the token type" in {
    val tokenType = RefreshTokenClaimsParser.getTokenType(refreshJwt)
    assert(tokenType.equals("refresh"))
  }
}
