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

import org.slf4j.LoggerFactory
import za.co.absa.loginsvc.rest.config.validation.{ConfigValidationException, ConfigValidationResult}
import za.co.absa.loginsvc.rest.config.validation.ConfigValidationResult.{ConfigValidationError, ConfigValidationSuccess}
import za.co.absa.loginsvc.utils.{AwsSecretsUtils, SecretUtils}

import java.security.{KeyFactory, KeyPair}
import java.security.spec.{PKCS8EncodedKeySpec, X509EncodedKeySpec}
import java.time.Instant
import java.util.Base64
import scala.concurrent.duration.{Duration, FiniteDuration}

case class AwsSecretsManagerKeyConfig(
  secretName: String,
  region: String,
  privateKeyFieldName: String,
  publicKeyFieldName: String,
  algName: String,
  accessExpTime: FiniteDuration,
  refreshExpTime: FiniteDuration,
  pollTime: Option[FiniteDuration],
  keyLayOverTime: Option[FiniteDuration],
  keyPhaseOutTime: Option[FiniteDuration]
) extends KeyConfig {

  private val logger = LoggerFactory.getLogger(classOf[AwsSecretsManagerKeyConfig])

  override def keyRotationTime : Option[FiniteDuration] = pollTime
  override def keyPair(): (KeyPair, Option[KeyPair]) = fetchKeySetsFromCloud()

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

  /**
   * Fetches the keypair used for generating Java Web Tokens from Cloud.
   * Fetches both the current as well as previously rotated keys if available.
   *
   * @param secretsUtils The methods used to fetch the keys.
   *                     Mainly used for testing and can be left empty to use the default value in standard use.
   * @return A tuple of the most current KeyPair as well as an option of the previously rotated keypair if available.
   *         The order and availability of the keys are dependant on key-lay-over and key-phase-out if enabled.
   */
  private[jwt] def fetchKeySetsFromCloud(secretsUtils: SecretUtils = AwsSecretsUtils): (KeyPair, Option[KeyPair]) = {
    try {
      val currentSecretsOption = secretsUtils.fetchSecret(
        secretName,
        region,
        Array(privateKeyFieldName, publicKeyFieldName),
        None
      )

      if(currentSecretsOption.isEmpty)
        throw new Exception("Error retrieving AWSCURRENT key from from AWS Secrets Manager")

      val currentSecrets = currentSecretsOption.get
      val currentKeyPair = createKeyPair(currentSecrets.secretValue)
      logger.info("AWSCURRENT Key Data successfully retrieved and parsed from AWS Secrets Manager")

      val previousSecretsOption =
        secretsUtils.fetchSecret(
          secretName,
          region,
          Array(privateKeyFieldName, publicKeyFieldName),
          Some("AWSPREVIOUS")
        )

      val previousKeyPair = previousSecretsOption.flatMap { previousSecrets =>
        try {
          val keys = createKeyPair(previousSecrets.secretValue)
          logger.info("AWSPREVIOUS Key Data successfully retrieved and parsed from AWS Secrets Manager")
          val keyPhaseOutActive = keyPhaseOutTime.exists(kpot =>
            isExpired(currentSecrets.createTime, kpot + keyLayOverTime.getOrElse(Duration.Zero)))
          if(keyPhaseOutActive) { None }
          else { Some(keys) }
        } catch {
          case e: Throwable =>
            logger.warn(s"Error occurred decoding AWSPREVIOUSKEYS, skipping previous keys.", e)
            None
        }
      }

      previousKeyPair.fold {(currentKeyPair, previousKeyPair)} { pk =>
        val keyLayOverActive = keyLayOverTime.exists(!isExpired(currentSecrets.createTime, _))
        if (!keyLayOverActive) {
          (currentKeyPair, previousKeyPair)
        }
        else {
          (pk, Some(currentKeyPair))
        }
      }
    } catch {
      case e: Throwable =>
        logger.error(s"Error occurred retrieving and decoding keys from AWS Secrets Manager", e)
        throw e
    }
  }

  private def createKeyPair(secretKeys: Map[String, String]): KeyPair = {

    val publicKeySpec: X509EncodedKeySpec = new X509EncodedKeySpec(
      Base64.getDecoder.decode(
        secretKeys(publicKeyFieldName)
      )
    )
    val privateKeySpec: PKCS8EncodedKeySpec = new PKCS8EncodedKeySpec(
      Base64.getDecoder.decode(
        secretKeys(privateKeyFieldName)
      )
    )

    val keyFactory: KeyFactory = KeyFactory.getInstance(jwtAlgorithmToCryptoAlgorithm)
    new KeyPair(keyFactory.generatePublic(publicKeySpec), keyFactory.generatePrivate(privateKeySpec))
  }

  private def isExpired(creationTime: Instant, finiteDuration: FiniteDuration): Boolean = {
    val expirationTime = creationTime.plus(finiteDuration.toMillis, java.time.temporal.ChronoUnit.MILLIS)
    Instant.now().isAfter(expirationTime)
  }
}
