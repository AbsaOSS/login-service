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

import java.text.SimpleDateFormat
import java.util
import java.util.Date
import scala.collection.JavaConverters._

/**
 * This object is used to parse Access Token claims.
 */

object ClaimsParser {

  //Generic methods for parsing JWT claims
  def listClaimKeys(jwt: Jwt): List[String] = {
    jwt.getClaims.keySet().asScala.toList
  }

  def getClaim(jwt: Jwt, claimKey: String): Option[Any] = {
    Option(jwt.getClaim(claimKey))
  }

  def getAllClaims(jwt: Jwt): Map[String, Any] = {
    jwt.getClaims.asScala.toMap
  }

  // Login Service specific methods for parsing JWT claims
  def getGroups(jwt: Jwt): List[String] = {
    getClaim(jwt, "groups") match {
      case Some(groups) => groups.asInstanceOf[util.ArrayList[String]].asScala.toList
      case None => List()
    }
  }

  def getSubject(jwt: Jwt): String = {
    getClaim(jwt, "sub") match {
      case Some(username) => username.toString
      case None => ""
    }
  }

  def getExpiration(jwt: Jwt): Option[Date] = {
    val dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
    getClaim(jwt, "exp") match {
      case Some(expiration) => Some(dateFormat.parse(expiration.toString))
      case None => None
    }
  }

  def getIssueTime(jwt: Jwt): Option[Date] = {
    val dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
    getClaim(jwt, "iat") match {
      case Some(issueTime) => Some(dateFormat.parse(issueTime.toString))
      case None => None
    }
  }

  def getEmail(jwt: Jwt): String = {
    getClaim(jwt, "email") match {
      case Some(email) => email.toString
      case None => ""
    }
  }

  def getDisplayName(jwt: Jwt): String = {
    getClaim(jwt, "displayname") match {
      case Some(displayName) => displayName.toString
      case None => ""
    }
  }

  def getTokenType(jwt: Jwt): String = {
    getClaim(jwt, "type") match {
      case Some(tokenType) => tokenType.toString
      case None => ""
    }
  }

}
