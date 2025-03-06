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

package za.co.absa.loginclient.publicKeyRetrieval.client

import com.google.gson.JsonParser
import com.nimbusds.jose.jwk.JWKSet
import org.slf4j.{Logger, LoggerFactory}
import org.springframework.web.client.RestTemplate
import za.co.absa.loginclient.publicKeyRetrieval.model.PublicKey

import scala.jdk.CollectionConverters.iterableAsScalaIterableConverter

/**
 * This class is used to retrieve the public key from the issuer.
 * public key is available in either a string or a JWK format.
 * @param host The issuer host.
 */

case class PublicKeyRetrievalClient(host: String) {

  private val logger: Logger = LoggerFactory.getLogger(this.getClass)

  /**
   * Retrieves the current public key from the login service as a PublicKey object.
   * This method fetches the public key used for JWT verification and returns it as a PublicKey object.
   * Key is available as a string within the object
   *
   * @return A PublicKey object representing the public key retrieved from the login service.
   */
  def getPublicKey: PublicKey = {
    val issuerUri = s"$host/token/public-key"
    val jsonString = fetchToken(issuerUri)
    val token = JsonParser.parseString(jsonString).getAsJsonObject.get("key").getAsString
    PublicKey(token)
  }

  /**
   * Retrieves all available public keys from the login service as a set of PublicKey objects.
   * This method fetches the public keys used for JWT verification and returns it as a set of PublicKey objects.
   * Keys are available as a string within the objects.
   *
   * @return A set of PublicKey objects representing the public keys retrieved from the login service.
   */
  def getPublicKeys: Set[PublicKey] = {
    val issuerUri = s"$host/token/public-keys"
    val jsonString = fetchToken(issuerUri)
    val publicKeyStrings = JsonParser.parseString(jsonString).getAsJsonObject.getAsJsonArray("keys")
      .asScala.map {jsonElement =>
        val obj = jsonElement.getAsJsonObject
        obj.entrySet().asScala.head.getValue.getAsString
      }.toSet

    publicKeyStrings.map { publicKeyString => PublicKey(publicKeyString) }
  }

  /**
   * Retrieves the public key from the login service as a JWK (JSON Web Key) set.
   * This method fetches the public key(s) used for JWT verification and returns it as a JWK Set format.
   *
   * @return A String containing the public key(s) in JWK (JSON Web Key) format retrieved from the login service.
   */
  def getPublicKeyJwk: JWKSet = {
    val issuerUri = s"$host/token/public-key-jwks"
    val jsonString = fetchToken(issuerUri)
    JWKSet.parse(jsonString)
  }

  private[client] def fetchToken(issuerUri: String): String = {

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
