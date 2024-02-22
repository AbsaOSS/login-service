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
import io.jsonwebtoken._
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import za.co.absa.loginsvc.model.User
import za.co.absa.loginsvc.rest.config.provider.JwtConfigProvider
import za.co.absa.loginsvc.rest.model.{AccessToken, RefreshToken, Token}
import za.co.absa.loginsvc.rest.service.JWTService.extractUserFrom

import java.security.interfaces.RSAPublicKey
import java.security.{KeyPair, PublicKey}
import java.time.Instant
import java.util.Date
import java.util.concurrent.{Executors, TimeUnit}
import scala.collection.JavaConverters._
import scala.compat.java8.DurationConverters._
import scala.concurrent.duration.FiniteDuration

@Service
class JWTService @Autowired()(jwtConfigProvider: JwtConfigProvider, authSearchService: AuthSearchService) {

  private val logger = LoggerFactory.getLogger(classOf[JWTService])
  private val scheduler = Executors.newSingleThreadScheduledExecutor(r => {
    val t = new Thread(r)
    t.setDaemon(true)
    t
  })

  private val jwtConfig = jwtConfigProvider.getJwtKeyConfig
  @volatile private var keyPair: KeyPair = jwtConfig.keyPair()

  if(jwtConfig.keyRotationTime.nonEmpty)
    {
      val refreshTime = jwtConfig.keyRotationTime.get
      scheduleSecretsRefresh(refreshTime)
    }

  def generateAccessToken(user: User, isRefresh: Boolean = false): AccessToken = {
    val msgIntro = if (isRefresh) "Refreshing" else "Generating new"
    logger.info(s"$msgIntro token for user: ${user.name}")

    import scala.collection.JavaConverters._

    val expiration = Date.from(
      Instant.now().plus(jwtConfig.accessExpTime.toJava)
    )
    val issuedAt = Date.from(Instant.now())
    // needs to be Java List/Array, otherwise incorrect claim is generated
    val groupsClaim = user.groups.asJava

    val tokenContent = Jwts
      .builder()
      .setSubject(user.name)
      .setExpiration(expiration)
      .setIssuedAt(issuedAt)
      .claim("kid", publicKeyThumbprint)
      .claim("groups", groupsClaim)
      .addClaims(
        user.optionalAttributes.collect {
          case (key, Some(value)) => key -> value
        }.asJava
      )
      .claim("type", Token.TokenType.Access.toString)
      .signWith(keyPair.getPrivate)
      .compact()

    AccessToken(tokenContent)
  }

  def generateRefreshToken(user: User): RefreshToken = {
    val expiration = Date.from(
      Instant.now().plus(jwtConfig.refreshExpTime.toJava)
    )

    val issuedAt = Date.from(Instant.now())

    val tokenContent = Jwts
      .builder()
      .setSubject(user.name)
      .setExpiration(expiration)
      .setIssuedAt(issuedAt)
      .claim("type", Token.TokenType.Refresh.toString)
      .signWith(keyPair.getPrivate)
      .compact()

    RefreshToken(tokenContent)
  }

  def refreshTokens(accessToken: AccessToken, refreshToken: RefreshToken): (AccessToken, RefreshToken) = {
    val oldAccessJws: Jws[Claims] = Jwts.parserBuilder()
      .require("type", Token.TokenType.Access.toString)
      .setSigningKey(keyPair.getPublic)
      .setClock(() => Date.from(Instant.now().minus(jwtConfig.refreshExpTime.toJava))) // allowing expired access token - up to refresh token validity window
      .build()
      .parseClaimsJws(accessToken.token) // checks requirements: type=access, signature, custom validity window

    val userFromOldAccessToken: User = extractUserFrom(oldAccessJws.getBody)

    val checkUser = authSearchService.searchUser(userFromOldAccessToken.name) // check if user still exists
    logger.info(checkUser.toString)

    Jwts.parserBuilder()
      .require("type", Token.TokenType.Refresh.toString)
      .requireSubject(userFromOldAccessToken.name)
      .setSigningKey(keyPair.getPublic)
      .build()
      .parseClaimsJws(refreshToken.token) // checks username, validity, and signature.


    val refreshedAccessToken = generateAccessToken(userFromOldAccessToken, isRefresh = true) // same process as with normal generation, but different msg

    // we are giving the original still-valid refreshToken back - potentially making room here to revoke or regenerate refreshTokens later
    (refreshedAccessToken, refreshToken)
  }

  def publicKey: PublicKey = keyPair.getPublic

  def publicKeyThumbprint: String = rsaPublicKey.getKeyID

  def jwks: JWKSet = {
    val jwk = rsaPublicKey
    new JWKSet(jwk).toPublicJWKSet
  }

  def close() : Unit = {
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
  }

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

  private def scheduleSecretsRefresh(refreshTime : FiniteDuration): Unit = {
      val scheduledFuture = scheduler.scheduleAtFixedRate(() => {
        logger.info("Attempting to Refresh for new Keys")
        try {
          val newKeyPair = jwtConfig.keyPair()
          logger.info("Keys have been Refreshed")
          keyPair = newKeyPair
        }
        catch {
          case e: Throwable =>
            logger.error(s"Error occurred retrieving and decoding Keys from AWS " +
              s"will attempt to retrieve Keys again in ${refreshTime.toString()}", e)
        }
      },refreshTime.toMillis,
        refreshTime.toMillis,
        TimeUnit.MILLISECONDS
      )

      Runtime.getRuntime.addShutdownHook(new Thread(() => {

        scheduledFuture.cancel(false)
        this.close()
      }))
  }

  def getConfiguredRefreshExpDuration: FiniteDuration = jwtConfig.refreshExpTime
}

object JWTService {

  private val requiredClaims =
    Seq("sub",
    "groups",
    "type",
    "exp",
    "iat",
    "kid")

  def extractUserFrom(claims: Claims): User = {
    val name = claims.getSubject
    val groups = claims.get("groups", classOf[java.util.List[String]]).asScala
    val optionalAttributes = claims.asScala.collect {
      case (key, value) if !requiredClaims.contains(key) => key -> Option(value)
    }.toMap

    User(name, groups, optionalAttributes)
  }
}
