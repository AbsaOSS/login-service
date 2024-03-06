package za.co.absa.loginsvc.utils

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import org.slf4j.LoggerFactory
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient
import software.amazon.awssdk.services.secretsmanager.model.{GetSecretValueRequest, GetSecretValueResponse}

object AwsSecretsUtils {

  private val logger = LoggerFactory.getLogger(getClass)
  def fetchSecret(secretName: String, region: String, secretFields: Array[String]): Map[String, String] = {

    val default = DefaultCredentialsProvider.create

    val client = SecretsManagerClient.builder
      .region(Region.of(region))
      .credentialsProvider(default)
      .build

    val getSecretValueRequest = GetSecretValueRequest.builder.secretId(secretName).build

    try {
      logger.info("Attempting to fetch secret from AWS Secrets Manager")
      val getSecretValueResponse: GetSecretValueResponse = client.getSecretValue(getSecretValueRequest)
      val secret = getSecretValueResponse.secretString
      logger.info("secret retrieved. Attempting to Parse data")
      val rootNode: JsonNode = new ObjectMapper().readTree(secret)

      secretFields.map(field => {
        field -> rootNode.get(field).asText()
      }).toMap
    }
    catch {
      case e: Throwable =>
        logger.error(s"Error occurred retrieving and parsing secrets from AWS Secrets Manager", e)
        throw e
    }
  }
}
