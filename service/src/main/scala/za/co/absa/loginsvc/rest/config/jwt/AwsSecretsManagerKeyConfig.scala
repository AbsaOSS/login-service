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

package za.co.absa.loginsvc.rest.config.jwt

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import io.jsonwebtoken.SignatureAlgorithm
import org.slf4j.LoggerFactory
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient
import software.amazon.awssdk.services.secretsmanager.model.{GetSecretValueRequest, GetSecretValueResponse}
import za.co.absa.loginsvc.rest.config.validation.{ConfigValidationException, ConfigValidationResult}
import za.co.absa.loginsvc.rest.config.validation.ConfigValidationResult.{ConfigValidationError, ConfigValidationSuccess}

import java.security.{KeyFactory, KeyPair}
import java.security.spec.{PKCS8EncodedKeySpec, X509EncodedKeySpec}
import java.util.Base64
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success, Try}

case class AwsSecretsManagerKeyConfig (secretName: String,
                                       region: String,
                                       privateAwsKey: String,
                                       publicAwsKey: String,
                                       algName: String,
                                       accessExpTime: FiniteDuration,
                                       refreshExpTime: FiniteDuration)
  extends KeyConfig {

  private val logger = LoggerFactory.getLogger(classOf[AwsSecretsManagerKeyConfig])

  override def keyPair: KeyPair = {

    val default = DefaultCredentialsProvider.create

    val client = SecretsManagerClient.builder
      .region(Region.of(region))
      .credentialsProvider(default)
      .build

    val getSecretValueRequest = GetSecretValueRequest.builder.secretId(secretName).build

    try {
      logger.info("Attempting to fetch key data from AWS Secrets Manager")
      val getSecretValueResponse: GetSecretValueResponse = client.getSecretValue(getSecretValueRequest)
      val secret = getSecretValueResponse.secretString
      logger.info("Key data retrieved. Attempting to Parse key data")
      val rootNode: JsonNode = new ObjectMapper().readTree(secret)

      val publicKeySpec: X509EncodedKeySpec = new X509EncodedKeySpec(
        Base64.getDecoder.decode(
          rootNode.get(publicAwsKey).asText()
        )
      )
      val privateKeySpec: PKCS8EncodedKeySpec = new PKCS8EncodedKeySpec(
        Base64.getDecoder.decode(
          rootNode.get(privateAwsKey).asText()
        )
      )

      logger.info("Key Data successfully retrieved and parsed from AWS Secrets Manager")

      val keyFactory: KeyFactory = KeyFactory.getInstance("RSA")
      new KeyPair(keyFactory.generatePublic(publicKeySpec), keyFactory.generatePrivate(privateKeySpec))
    }
    catch {
      case e: Throwable =>
        logger.error(s"Error occurred retrieving and decoding keys from AWS Secrets Manager", e)
        throw e
    }
  }
  override def throwErrors(): Unit = this.validate().throwOnErrors()
  override def validate(): ConfigValidationResult = {

    val results = Seq(
      Option(secretName)
        .map(_ => ConfigValidationSuccess)
        .getOrElse(ConfigValidationError(ConfigValidationException("secretName is empty"))),

      Option(region)
        .map(_ => ConfigValidationSuccess)
        .getOrElse(ConfigValidationError(ConfigValidationException("region is empty"))),

      Option(privateAwsKey)
        .map(_ => ConfigValidationSuccess)
        .getOrElse(ConfigValidationError(ConfigValidationException("privateAwsKey is empty"))),

      Option(publicAwsKey)
        .map(_ => ConfigValidationSuccess)
        .getOrElse(ConfigValidationError(ConfigValidationException("publicAwsKey is empty"))),
    )

    val resultsMerge = results.foldLeft[ConfigValidationResult](ConfigValidationSuccess)(ConfigValidationResult.merge)

    val algValidation = Try {
      SignatureAlgorithm.valueOf(algName)
    } match {
      case Success(_) => ConfigValidationSuccess
      case Failure(e: IllegalArgumentException) if e.getMessage.contains("No enum constant") =>
        ConfigValidationError(ConfigValidationException(s"Invalid algName '$algName' was given."))
      case Failure(e) => throw e
    }

    val accessExpTimeResult = if (accessExpTime < minAccessExpTime) {
      ConfigValidationError(ConfigValidationException(s"accessExpTime must be at least $minAccessExpTime"))
    } else ConfigValidationSuccess

    val refreshExpTimeResult = if (refreshExpTime < minRefreshExpTime) {
      ConfigValidationError(ConfigValidationException(s"refreshExpTime must be at least $minRefreshExpTime"))
    } else ConfigValidationSuccess

    resultsMerge.merge(algValidation).merge(accessExpTimeResult).merge(refreshExpTimeResult)
  }
}
