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


package za.co.absa.loginclient.tokenRetrieval.service

import com.google.gson.{JsonObject, JsonParser}
import com.nimbusds.jose.jwk.JWK
import org.slf4j.{Logger, LoggerFactory}
import org.springframework.http.{HttpEntity, HttpHeaders, HttpMethod, MediaType, ResponseEntity}
import org.springframework.web.client.RestTemplate
import za.co.absa.loginclient.tokenRetrieval.model.{AccessToken, PublicKey, RefreshToken}

import java.net.URLEncoder
import java.util.Collections

case class RetrieveToken(host: String) {

  private val logger: Logger = LoggerFactory.getLogger(this.getClass)

  def getPublicKey: PublicKey = {
    val issuerUri = s"$host/token/public-key"
    val jsonString = fetchToken(issuerUri)
    val token = JsonParser.parseString(jsonString).getAsJsonObject.get("key").getAsString
    PublicKey(token)
  }

  def getPublicKeyJwk: JWK = {
    val issuerUri = s"$host/token/public-key-jwks"
    val jsonString = fetchToken(issuerUri)
    val jwkString = JsonParser.parseString(jsonString).getAsJsonObject.get("key").getAsString
    JWK.parse(jwkString)
  }

  def fetchAccessToken(username: String, password: String): AccessToken = {
    fetchAccessAndRefreshToken(username, password)._1
  }

  def fetchRefreshToken(username: String, password: String): RefreshToken = {
    fetchAccessAndRefreshToken(username, password)._2
  }

  def fetchAccessAndRefreshToken(username: String, password: String, groups: Option[List[String]] = None): (AccessToken, RefreshToken) = {
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

  private def fetchToken(issuerUri: String): String = {

    logger.info(s"Fetching token from $issuerUri")

    val restTemplate = new RestTemplate()
    try {
      val response = restTemplate.getForEntity(issuerUri, classOf[String])
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
