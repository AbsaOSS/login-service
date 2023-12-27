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

import org.springframework.security.oauth2.jwt.Jwt

import java.time.{Instant, LocalDateTime, ZoneId}
import java.util
import scala.collection.JavaConverters._

/**
 * This object is used to parse Access Token claims.
 */

object ClaimsParser {

  //Generic methods for parsing JWT claims

  /**
   * Returns a list of all the claim keys in the JWT.
   *
   * @param jwt The JWT to parse.
   * @return A list of all the claim keys in the JWT.
   */
  def listClaimKeys(jwt: Jwt): List[String] = {
    jwt.getClaims.keySet().asScala.toList
  }

  /**
   * Returns the value of the claim with the given key.
   *
   * @param jwt      The JWT to parse.
   * @param claimKey The key of the claim to retrieve.
   * @return The value of the claim with the given key.
   */
  def getClaim(jwt: Jwt, claimKey: String): Option[Any] = {
    Option(jwt.getClaim(claimKey))
  }

  /**
   * Returns a map of all the claims in the JWT.
   *
   * @param jwt The JWT to parse.
   * @return A map of all the claims in the JWT.
   */
  def getAllClaims(jwt: Jwt): Map[String, Any] = {
    jwt.getClaims.asScala.toMap
  }

  // Login Service specific methods for parsing JWT claims

  /**
   * Returns the list of groups of the user that the JWT was issued to.
   *
   * @param jwt The JWT to parse.
   * @return The List of groups of the user that the JWT was issued to.
   */
  def getGroups(jwt: Jwt): List[String] = {
    getClaim(jwt, "groups") match {
      case Some(groups) => groups.asInstanceOf[util.ArrayList[String]].asScala.toList
      case None => List()
    }
  }

  /**
   * Returns the username of the user that the JWT was issued to.
   *
   * @param jwt The JWT to parse.
   * @return The username of the user that the JWT was issued to.
   */
  def getSubject(jwt: Jwt): String = {
    getClaim(jwt, "sub") match {
      case Some(username) => username.toString
      case None => throw new Exception("Subject not found")
    }
  }

  /**
   * Returns the expiry time of the JWT.
   *
   * @param jwt The JWT to parse.
   * @return The expiry time of the JWT.
   */
  def getExpiration(jwt: Jwt): Instant = {
    getClaim(jwt, "exp") match {
      case Some(expiration) => Instant.ofEpochSecond(expiration.toString.toLong)
      case None => throw new Exception("Expiration not found")
    }
  }

  /**
   * Returns the issue time of the JWT.
   *
   * @param jwt The JWT to parse.
   * @return The issue time of the JWT.
   */
  def getIssueTime(jwt: Jwt): Instant = {
    getClaim(jwt, "iat") match {
      case Some(issueTime) => Instant.ofEpochSecond(issueTime.toString.toLong)
      case None => throw new Exception("Issue time not found")
    }
  }

  /**
   * Returns the email of the user that the JWT was issued to.
   * @param jwt The JWT to parse.
   * @return The email of the user that the JWT was issued to.
   */
  def getEmail(jwt: Jwt): Option[String] = {
    getClaim(jwt, "email") match {
      case Some(email) => Some(email.toString)
      case None => None
    }
  }

  /**
   * Returns the display name of the user that the JWT was issued to.
   * @param jwt The JWT to parse.
   * @return The display name of the user that the JWT was issued to.
   */
  def getDisplayName(jwt: Jwt): Option[String] = {
    getClaim(jwt, "displayname") match {
      case Some(displayName) => Some(displayName.toString)
      case None => None
    }
  }

  /**
   * Returns the type of JWT. Can be either an access or refresh token.
   * @param jwt The JWT to parse.
   * @return The type of JWT as a String.
   */
  def getTokenType(jwt: Jwt): String = {
    getClaim(jwt, "type") match {
      case Some(tokenType) => tokenType.toString
      case None => throw new Exception("Token type not found")
    }
  }

  /**
   * Verifies that the JWT is a valid access token.
   * Checks that the token is not expired and that the type is access.
   * @param jwt The JWT to parse.
   * @return True if the JWT is a valid access token, false otherwise.
   */
  def verifyDecodedAccessToken(jwt: Jwt): Boolean = {
    val claims = getAllClaims(jwt)
    verifyDecodedAccessToken(claims)
  }

  /**
   * Verifies that the JWT is a valid access token.
   * Checks that the token is not expired and that the type is access.
   * @param claims The claims of the JWT to parse.
   * @return True if the JWT is a valid access token, false otherwise.
   */
  def verifyDecodedAccessToken(claims: Map[String, Any]): Boolean = {
    val exp = Instant.parse(claims("exp").toString).getEpochSecond
    val notExpired = exp > Instant.now().getEpochSecond
    val isAccessType = claims("type").toString == "access"
    notExpired && isAccessType
  }
}