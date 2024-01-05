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

import io.jsonwebtoken.security.Keys
import io.jsonwebtoken.{Jwts, SignatureAlgorithm}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

import java.time.Instant
import java.util
import java.util.{Base64, Date}

class ClaimsParserTest extends AnyFlatSpec with Matchers {

  private val subject = "testUser"
  private val expiration = Date.from(Instant.now().plus(java.time.Duration.ofHours(1)))
  private val issuedAt = Date.from(Instant.now())
  private val groupsClaim: util.ArrayList[String] = new util.ArrayList()
  private val email = "testuser@org.com"
  private val displayName = "Test User"
  private val tokenType = "access"

  groupsClaim.add("group1")
  groupsClaim.add("group2")

  private val keys = Keys.keyPairFor(SignatureAlgorithm.RS256)

  private val token = Jwts.builder()
    .setSubject(subject)
    .setExpiration(expiration)
    .setIssuedAt(issuedAt)
    .claim("groups", groupsClaim)
    .claim("email", email)
    .claim("displayname", displayName)
    .claim("type", tokenType)
    .signWith(keys.getPrivate)
    .compact()

  private val publicKeyString = Base64.getEncoder.encodeToString(keys.getPublic.getEncoded)
  private val decoder = JwtDecoderProvider.getDecoderFromPublicKeyString(publicKeyString)
  private val jwt = decoder.decode(token)

  it should "return a subject that equals 'testUser'" in {
    val sub = AccessTokenClaimsParser.getSubject(jwt)
    assert(sub.equals(subject))
  }

  it should "return a list of groups that contain 'group1', 'group2')" in {
    val groups = AccessTokenClaimsParser.getGroups(jwt)
    assert(groups == List("group1", "group2"))
  }

  it should "return a expiration date that should be within an hour of testing" in {
    val exp = AccessTokenClaimsParser.getExpiration(jwt)
    val check = expiration.toInstant
    assert(exp.equals(Instant.ofEpochSecond(check.getEpochSecond)))
  }

  it should "return the set time of issue" in {
    val issueTime = AccessTokenClaimsParser.getIssueTime(jwt)
    val check = issuedAt.toInstant
    assert(issueTime.equals(Instant.ofEpochSecond(check.getEpochSecond)))
  }

  it should "return the email address" in {
    val email = AccessTokenClaimsParser.getEmail(jwt)
    assert(email.equals(email))
  }

  it should "return the display name" in {
    val displayName = AccessTokenClaimsParser.getDisplayName(jwt)
    assert(displayName.equals(displayName))
  }

  it should "return the token type" in {
    val tokenType = AccessTokenClaimsParser.getTokenType(jwt)
    assert(tokenType.equals(tokenType))
  }
}
