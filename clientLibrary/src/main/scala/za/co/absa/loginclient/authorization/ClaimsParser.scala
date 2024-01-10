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
import za.co.absa.loginclient.exceptions.LsJwtException

import java.time.Instant
import java.util
import scala.collection.JavaConverters._

trait ClaimsParser {

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

  /**
   * Returns the username of the user that the JWT was issued to.
   *
   * @param jwt The JWT to parse.
   * @return The username of the user that the JWT was issued to.
   */
  def getSubject(jwt: Jwt): String = {
    getClaim(jwt, "sub") match {
      case Some(username) => username.toString
      case None => throw LsJwtException("Subject not found")
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
      case Some(expiration) => Instant.parse(expiration.toString)
      case None => throw LsJwtException("Expiration not found")
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
      case Some(issueTime) => Instant.parse(issueTime.toString)
      case None => throw LsJwtException("Issue time not found")
    }
  }

  /**
   * Returns the type of JWT. Can be either an access or refresh token.
   *
   * @param jwt The JWT to parse.
   * @return The type of JWT as a String.
   */
  def getTokenType(jwt: Jwt): String = {
    getClaim(jwt, "type") match {
      case Some(tokenType) => tokenType.toString
      case None => throw LsJwtException("Token type not found")
    }
  }
}

/**
 * This object is used to parse Access Token claims.
 */

object AccessTokenClaimsParser extends ClaimsParser {

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
   * Checks the type of JWT to be 'access'
   *
   * @param jwt The JWT to parse.
   * @return true if type=access was found, false otherwise
   * @throws LsJwtException if `type` claim was not found at all

   */

  def isAccessTokenType(jwt: Jwt): Boolean = {
    getTokenType(jwt) == "access"
  }
}

/**
 * This object is used to parse Refresh Token claims.
 */
object RefreshTokenClaimsParser extends ClaimsParser {

  /**
   * Checks the type of JWT to be 'refresh'
   *
   * @param jwt The JWT to parse.
   * @return true if type=refresh was found, false otherwise
   * @throws LsJwtException if `type` claim was not found at all
   */

  def isRefreshTokenType(jwt: Jwt): Boolean = {
    getTokenType(jwt)  == "refresh"
  }
}