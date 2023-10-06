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

package za.co.absa.loginsvc.rest.service

import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.{JWKSet, KeyUse, RSAKey}
import io.jsonwebtoken.security.Keys
import io.jsonwebtoken.{JwtBuilder, Jwts, SignatureAlgorithm}
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse
import za.co.absa.loginsvc.model.User
import za.co.absa.loginsvc.rest.config.FetchSecretConfig
import za.co.absa.loginsvc.rest.config.provider.JwtConfigProvider
import za.co.absa.loginsvc.rest.service.JWTService.JwtBuilderExt
import za.co.absa.loginsvc.utils.OptionExt

import java.security.interfaces.RSAPublicKey
import java.security.spec.{PKCS8EncodedKeySpec, X509EncodedKeySpec}
import java.security.{KeyFactory, KeyPair, PublicKey}
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.{Executors, TimeUnit}
import java.util.{Base64, Date}

@Service
class JWTService @Autowired()(jwtConfigProvider: JwtConfigProvider) {

  private val logger = LoggerFactory.getLogger(classOf[JWTService])

  private val jwtConfig = jwtConfigProvider.getJWTConfig
  @volatile private var config: keyConfig = if(jwtConfig.fetchFromAws.nonEmpty)
    {
      logger.info("Fetching Keys from AWS Secrets Manager")
      val awsConfig = jwtConfig.fetchFromAws.get
      refreshSecrets(awsConfig)
      fetchSecrets(awsConfig)
    }
  else
    {
      logger.info("Generating Keys in Memory")
      val generateConfig = jwtConfig.generateInMemory.get
      keyConfig(Keys.keyPairFor(SignatureAlgorithm.valueOf(generateConfig.algName)),
        generateConfig.expTime,
        generateConfig.algName)
    }

  def generateToken(user: User): String = {
    import scala.collection.JavaConverters._


    val expiration = Date.from(
      Instant.now().plus(config.expTime, ChronoUnit.HOURS)
    )
    val issuedAt = Date.from(Instant.now())
    // needs to be Java List/Array, otherwise incorrect claim is generated
    val groupsClaim = user.groups.asJava

    Jwts
      .builder()
      .setSubject(user.name)
      .setExpiration(expiration)
      .setIssuedAt(issuedAt)
      .claim("kid", publicKeyThumbprint)
      .claim("groups", groupsClaim)
      .applyIfDefined(user.email, (builder, value: String) => builder.claim("email", value))
      .applyIfDefined(user.displayName, (builder, value: String) => builder.claim("displayname", value))
      .signWith(config.keyPair.getPrivate)
      .compact()
  }

  def publicKey: PublicKey = config.keyPair.getPublic

  private def rsaPublicKey: RSAKey = {
    publicKey match {
      case rsaKey: RSAPublicKey => new RSAKey.Builder(rsaKey)
        .keyUse(KeyUse.SIGNATURE)
        .algorithm(JWSAlgorithm.parse(config.algName))
        .keyIDFromThumbprint()
        .build()
      case _ => throw new IllegalArgumentException("Unsupported public key type")
    }
  }

  def publicKeyThumbprint: String = rsaPublicKey.getKeyID

  def jwks: JWKSet = {
    val jwk = rsaPublicKey
    new JWKSet(jwk).toPublicJWKSet
  }

  private def fetchSecrets(config: FetchSecretConfig): keyConfig = {
    val default = DefaultCredentialsProvider.create

    val client = SecretsManagerClient.builder
      .region(Region.of(config.region))
      .credentialsProvider(default)
      .build

    val getSecretValueRequest = GetSecretValueRequest.builder.secretId(config.secretName).build

    try
    {
      logger.info("Attempting to fetch key data from AWS Secrets Manager")
      val getSecretValueResponse: GetSecretValueResponse =  client.getSecretValue(getSecretValueRequest)
      val secret = getSecretValueResponse.secretString
      logger.info("Key data retrieved. Attempting to Parse key data")
      val rootNode: JsonNode = new ObjectMapper().readTree(secret)

      val publicKeySpec: X509EncodedKeySpec = new X509EncodedKeySpec(
        Base64.getDecoder.decode(
          rootNode.get(config.publicAwsKey).asText()
        )
      )
      val privateKeySpec: PKCS8EncodedKeySpec = new PKCS8EncodedKeySpec(
        Base64.getDecoder.decode(
          rootNode.get(config.privateAwsKey).asText()
        )
      )

      val exp = rootNode.get(config.expAwsKey).asInt()
      val alg = rootNode.get(config.algAwsKey).asText()

      logger.info("Key Data successfully retrieved and parsed from AWS Secrets Manager")

      val keyFactory: KeyFactory = KeyFactory.getInstance("RSA")
      keyConfig (new KeyPair(keyFactory.generatePublic(publicKeySpec),
        keyFactory.generatePrivate(privateKeySpec)), exp, alg)
    }
    catch {
      case e: Throwable =>
        logger.error(s"Error occurred retrieving and decoding keys from AWS Secrets Manager", e)
        throw e
    }
  }

  private def refreshSecrets(secretConfig: FetchSecretConfig): Unit = {
      val scheduler = Executors.newSingleThreadScheduledExecutor()
      val scheduledFuture = scheduler.scheduleAtFixedRate(() => {
        try this.config = fetchSecrets(secretConfig)
        catch {
          case e: Throwable =>
            logger.error(s"Error occurred retrieving and decoding Keys from AWS " +
              s"will attempt to retrieve Keys again in ${secretConfig.refreshTime} seconds", e)
        }
      }, secretConfig.refreshTime,
        secretConfig.refreshTime,
        TimeUnit.MINUTES
      )

      Runtime.getRuntime.addShutdownHook(new Thread(() => {

        scheduledFuture.cancel(false)
        scheduler.shutdown()

        try {
          // Wait for up to 5 seconds for the scheduler to terminate
          if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
            // If it doesn't terminate, forcefully shut it down
            scheduler.shutdownNow()
          }
        }
        catch {
          case e: InterruptedException =>
            Thread.currentThread().interrupt()
        }
      }))
  }

  private case class keyConfig (keyPair: KeyPair, expTime: Int, algName: String)
}

object JWTService {
  implicit class JwtBuilderExt(val jwtBuilder: JwtBuilder) extends AnyVal {
    def applyIfDefined[T](opt: Option[T], fn: (JwtBuilder, T) => JwtBuilder): JwtBuilder = {
      OptionExt.applyIfDefined(jwtBuilder, opt, fn)
    }
  }
}
