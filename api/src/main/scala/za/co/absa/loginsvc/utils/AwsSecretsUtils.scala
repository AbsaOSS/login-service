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

package za.co.absa.loginsvc.utils

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import org.slf4j.LoggerFactory
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient
import software.amazon.awssdk.services.secretsmanager.model.{GetSecretValueRequest, GetSecretValueResponse}
import za.co.absa.loginsvc.rest.model.AwsSecret

object AwsSecretsUtils extends SecretUtils {

  private val logger = LoggerFactory.getLogger(getClass)
  def fetchSecret(
    secretName: String,
    region: String,
    secretFields: Array[String],
    versionStage: Option[String] = None
  ): Option[AwsSecret] = {

    val default = DefaultCredentialsProvider.create

    val client = SecretsManagerClient.builder
      .region(Region.of(region))
      .credentialsProvider(default)
      .build

    val getSecretValueRequestBuilder = GetSecretValueRequest.builder.secretId(secretName)
    versionStage.foreach(getSecretValueRequestBuilder.versionStage)
    val getSecretValueRequest = getSecretValueRequestBuilder.build()

    try {
      logger.info("Attempting to fetch secret from AWS Secrets Manager")
      val getSecretValueResponse: GetSecretValueResponse = client.getSecretValue(getSecretValueRequest)
      val secret = getSecretValueResponse.secretString
      logger.info("secret retrieved. Attempting to Parse data")
      val rootNode: JsonNode = new ObjectMapper().readTree(secret)

      val secretValues = secretFields.map(field => {
        field -> rootNode.get(field).asText()
      }).toMap
      val createTime = getSecretValueResponse.createdDate()

      Option(AwsSecret(secretValues, createTime))
    }
    catch {
      case e: Throwable =>
        logger.warn(s"Error occurred retrieving and parsing secrets from AWS Secrets Manager", e)
        None
    }
  }
}

trait SecretUtils {
  def fetchSecret(
    secretName: String,
    region: String,
    secretFields: Array[String],
    versionStage: Option[String] = None
  ): Option[AwsSecret]
}
