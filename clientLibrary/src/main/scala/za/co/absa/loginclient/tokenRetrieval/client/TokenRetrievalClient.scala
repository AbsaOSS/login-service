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

package za.co.absa.loginclient.tokenRetrieval.client

import com.google.gson.{JsonObject, JsonParser}
import org.slf4j.{Logger, LoggerFactory}
import org.springframework.http.{HttpEntity, HttpHeaders, HttpMethod, MediaType, ResponseEntity}
import org.springframework.web.client.RestTemplate
import za.co.absa.loginclient.tokenRetrieval.model.{AccessToken, RefreshToken}

import java.net.URLEncoder
import java.util.Collections

/**
 * This class is used to retrieve tokens from the login service.
 * Refresh and Access Keys require authorization. Basic Auth is used for the initial retrieval.
 * Refresh token from initial retrieval is used to refresh the access token.
 * @param host The host of the login service.
 */

case class TokenRetrievalClient(host: String) {

  private val logger: Logger = LoggerFactory.getLogger(this.getClass)

  /**
   * This method requests an access token (JWT) from the login service using the specified username and password.
   * This Token is used to access resources which utilize the login Service for authentication.
   *
   * @param username The username used for authentication.
   * @param password The password associated with the provided username.
   * @param groups   An optional list of PAM groups. If provided, only JWTs associated with these groups are returned if the user belongs to them.
   * @return An AccessToken object representing the retrieved access token (JWT) from the login service.
   */
  def fetchAccessToken(username: String, password: String, groups: List[String] = List.empty): AccessToken = {
    fetchAccessAndRefreshToken(username, password, groups)._1
  }

  /**
   * This method requests a refresh token from the login service using the specified username and password.
   * This token may be used to acquire a new access token (JWT) when the current access token expires.
   *
   * @param username The username used for authentication.
   * @param password The password associated with the provided username.
   * @return A RefreshToken object representing the retrieved refresh token from the login service.
   */
  def fetchRefreshToken(username: String, password: String): RefreshToken = {
    fetchAccessAndRefreshToken(username, password)._2
  }

  /**
   * Fetches both an access token and a refresh token from the login service using the provided username, password, and optional groups.
   * This method requests both an access token and a refresh token (JWTs) from the login service using the specified username and password.
   * Additionally, it allows specifying optional groups that act as filters for the JWT, returning only the JWTs associated with the provided groups if the user belongs to them.
   *
   * @param username The username used for authentication.
   * @param password The password associated with the provided username.
   * @param groups   An optional list of PAM groups. If provided, only JWTs associated with these groups are returned if the user belongs to them.
   * @return A tuple containing the AccessToken and RefreshToken objects representing the retrieved access and refresh tokens (JWTs) from the login service.
   */
  def fetchAccessAndRefreshToken(username: String, password: String, groups: List[String] = List.empty): (AccessToken, RefreshToken) = {
    var issuerUri = s"$host/token/generate"

    if(groups.nonEmpty)
      {
        val commaSeparatedString = groups.mkString(",")
        issuerUri += "?group-prefixes=" + URLEncoder.encode(commaSeparatedString, "UTF-8")
      }
    val jsonString = fetchToken(issuerUri, username, password)
    val jsonObject = JsonParser.parseString(jsonString).getAsJsonObject
    val accessToken = jsonObject.get("token").getAsString
    val refreshToken = jsonObject.get("refresh").getAsString
    (AccessToken(accessToken), RefreshToken(refreshToken))
  }

  def refreshAccessToken(accessToken: AccessToken, refreshToken: RefreshToken): (AccessToken, RefreshToken) = {
    val issuerUri = s"$host/token/refresh"

    logger.info(s"Refreshing Access token from $issuerUri")

    val jsonPayload: JsonObject = new JsonObject()
    jsonPayload.addProperty("token", accessToken.token)
    jsonPayload.addProperty("refresh", refreshToken.token)

    val headers: HttpHeaders  = new HttpHeaders()
    headers.setContentType(MediaType.APPLICATION_JSON)
    headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON))

    val requestEntity = new HttpEntity[String] (jsonPayload.toString, headers)

    val restTemplate: RestTemplate = new RestTemplate()

    try {
      val response: ResponseEntity[String] = restTemplate.exchange(
        issuerUri,
        HttpMethod.POST,
        requestEntity,
        classOf[String]
      )
      val jsonObject = JsonParser.parseString(response.getBody).getAsJsonObject
      logger.info("Successfully refreshed token")
      (
        AccessToken(jsonObject.get("token").getAsString),
        RefreshToken(jsonObject.get("refresh").getAsString)
      )
    }
    catch {
      case e: Throwable =>
        logger.error(s"Error occurred refreshing and decoding Token from $issuerUri", e)
        throw e
    }
  }

  private def fetchToken(issuerUri: String, username: String, password: String): String = {

    logger.info(s"Fetching token from $issuerUri for user $username")

    val headers = new HttpHeaders()
    val base64Credentials = java.util.Base64.getEncoder.encodeToString(s"$username:$password".getBytes)
    headers.set("Authorization", s"Basic $base64Credentials")

    val requestEntity = new HttpEntity[String](null, headers)

    val restTemplate = new RestTemplate()

    try {
      val response: ResponseEntity[String] = restTemplate.exchange(
        issuerUri,
        HttpMethod.POST,
        requestEntity,
        classOf[String]
      )
      logger.info("Successfully fetched token")
      response.getBody
    }
    catch {
      case e: Throwable =>
        logger.error(s"Error occurred retrieving and decoding Token from $issuerUri", e)
        throw e
    }
  }
}
