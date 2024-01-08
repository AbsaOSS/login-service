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

import io.jsonwebtoken.{Jwts, SignatureAlgorithm}
import io.jsonwebtoken.security.Keys

import java.security.KeyPair
import java.time.Instant
import java.util
import java.util.Date

object FakeTokens {

  val subject: String = "testUser"
  val validExpiration: Date = Date.from(Instant.now().plus(java.time.Duration.ofHours(1)))
  val invalidExpiration: Date = Date.from(Instant.now().minus(java.time.Duration.ofHours(1)))
  val issuedAt: Date = Date.from(Instant.now())
  val groupsClaim: util.ArrayList[String] = new util.ArrayList()
  groupsClaim.add("group1")
  groupsClaim.add("group2")
  val email: String = "testuser@org.com"
  val displayName: String = "Test User"

  val keys: KeyPair = Keys.keyPairFor(SignatureAlgorithm.RS256)
  val invalidKeys: KeyPair = Keys.keyPairFor(SignatureAlgorithm.RS256)

  val validAccessToken: String = Jwts.builder()
    .setSubject(subject)
    .setExpiration(validExpiration)
    .setIssuedAt(issuedAt)
    .claim("groups", groupsClaim)
    .claim("email", email)
    .claim("displayname", displayName)
    .claim("type", "access")
    .signWith(keys.getPrivate)
    .compact()

  val validRefreshToken: String = Jwts.builder()
    .setSubject(subject)
    .setExpiration(validExpiration)
    .setIssuedAt(issuedAt)
    .claim("type", "refresh")
    .signWith(keys.getPrivate)
    .compact()

  val invalidExpirationAccessToken: String = Jwts.builder()
    .setSubject(subject)
    .setExpiration(invalidExpiration)
    .setIssuedAt(issuedAt)
    .claim("groups", groupsClaim)
    .claim("email", email)
    .claim("displayname", displayName)
    .claim("type", "access")
    .signWith(keys.getPrivate)
    .compact()

  val invalidExpirationRefreshToken: String = Jwts.builder()
    .setSubject(subject)
    .setExpiration(invalidExpiration)
    .setIssuedAt(issuedAt)
    .claim("type", "refresh")
    .signWith(keys.getPrivate)
    .compact()

  val invalidSignatureAccessToken: String = Jwts.builder()
    .setSubject(subject)
    .setExpiration(validExpiration)
    .setIssuedAt(issuedAt)
    .claim("groups", groupsClaim)
    .claim("email", email)
    .claim("displayname", displayName)
    .claim("type", "access")
    .signWith(invalidKeys.getPrivate)
    .compact()

  val invalidSignatureRefreshToken: String = Jwts.builder()
    .setSubject(subject)
    .setExpiration(validExpiration)
    .setIssuedAt(issuedAt)
    .claim("type", "refresh")
    .signWith(invalidKeys.getPrivate)
    .compact()
}
