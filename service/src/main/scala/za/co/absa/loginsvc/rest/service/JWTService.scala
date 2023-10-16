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

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.{JWKSet, KeyUse, RSAKey}
import io.jsonwebtoken.{JwtBuilder, Jwts}
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import za.co.absa.loginsvc.model.User
import za.co.absa.loginsvc.rest.config.provider.JwtConfigProvider
import za.co.absa.loginsvc.rest.service.JWTService.JwtBuilderExt
import za.co.absa.loginsvc.utils.OptionExt

import java.security.interfaces.RSAPublicKey
import java.security.{KeyPair, PublicKey}
import java.time.Instant
import java.util.concurrent.{Executors, TimeUnit}
import java.util.Date
import scala.collection.JavaConverters._
import scala.compat.java8.DurationConverters._

@Service
class JWTService @Autowired()(jwtConfigProvider: JwtConfigProvider) {

  private val logger = LoggerFactory.getLogger(classOf[JWTService])

  private val jwtConfig = jwtConfigProvider.getJWTConfig
  @volatile private var keyPair: KeyPair = jwtConfig.keyPair

  refreshSecrets()

  def generateToken(user: User): String = {
    logger.info(s"Generating Token for user: ${user.name}")

    val expiration = Date.from(
      Instant.now().plus(jwtConfig.accessExpTime.toJava)
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
      .signWith(keyPair.getPrivate)
      .compact()
  }

  def publicKey: PublicKey = keyPair.getPublic

  private def rsaPublicKey: RSAKey = {
    publicKey match {
      case rsaKey: RSAPublicKey => new RSAKey.Builder(rsaKey)
        .keyUse(KeyUse.SIGNATURE)
        .algorithm(JWSAlgorithm.parse(jwtConfig.algName))
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

  private def refreshSecrets(): Unit = {
      val scheduler = Executors.newSingleThreadScheduledExecutor()
      val scheduledFuture = scheduler.scheduleAtFixedRate(() => {
        logger.info("Refreshing Keys")
        try keyPair = jwtConfig.keyPair
        catch {
          case e: Throwable =>
            logger.error(s"Error occurred retrieving and decoding Keys from AWS " +
              s"will attempt to retrieve Keys again in ${jwtConfig.refreshKeyTime.toString()}", e)
        }
      },jwtConfig.refreshKeyTime.toMillis,
        jwtConfig.refreshKeyTime.toMillis,
        TimeUnit.MILLISECONDS
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
          case _: InterruptedException =>
            Thread.currentThread().interrupt()
        }
      }))
  }
}

object JWTService {
  implicit class JwtBuilderExt(val jwtBuilder: JwtBuilder) extends AnyVal {
    def applyIfDefined[T](opt: Option[T], fn: (JwtBuilder, T) => JwtBuilder): JwtBuilder = {
      OptionExt.applyIfDefined(jwtBuilder, opt, fn)
    }
  }
}
