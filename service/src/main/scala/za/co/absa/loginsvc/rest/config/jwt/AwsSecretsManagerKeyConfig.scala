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
                                       privateKeyFieldName: String,
                                       publicKeyFieldName: String,
                                       algName: String,
                                       accessExpTime: FiniteDuration,
                                       pollTime: Option[FiniteDuration])
  extends KeyConfig {

  private val logger = LoggerFactory.getLogger(classOf[AwsSecretsManagerKeyConfig])

  override def refreshKeyTime : Option[FiniteDuration] = pollTime
  override def keyPair(): KeyPair = {

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
          rootNode.get(publicKeyFieldName).asText()
        )
      )
      val privateKeySpec: PKCS8EncodedKeySpec = new PKCS8EncodedKeySpec(
        Base64.getDecoder.decode(
          rootNode.get(privateKeyFieldName).asText()
        )
      )

      logger.info("Key Data successfully retrieved and parsed from AWS Secrets Manager")

      val keyFactory: KeyFactory = KeyFactory.getInstance(jwtAlgorithmToCryptoAlgorithm)
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

    val awsSecretsResults = Seq(
      Option(secretName)
        .map(_ => ConfigValidationSuccess)
        .getOrElse(ConfigValidationError(ConfigValidationException("secretName is empty"))),

      Option(region)
        .map(_ => ConfigValidationSuccess)
        .getOrElse(ConfigValidationError(ConfigValidationException("region is empty"))),

      Option(privateKeyFieldName)
        .map(_ => ConfigValidationSuccess)
        .getOrElse(ConfigValidationError(ConfigValidationException("privateKeyFieldName is empty"))),

      Option(publicKeyFieldName)
        .map(_ => ConfigValidationSuccess)
        .getOrElse(ConfigValidationError(ConfigValidationException("publicKeyFieldName is empty"))),
    )

    val awsSecretsResultsMerge = awsSecretsResults.foldLeft[ConfigValidationResult](ConfigValidationSuccess)(ConfigValidationResult.merge)

    super.validate().merge(awsSecretsResultsMerge)
  }
}
