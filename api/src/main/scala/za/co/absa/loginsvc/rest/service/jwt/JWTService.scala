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

package za.co.absa.loginsvc.rest.service.jwt

import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.jwk.{JWK, JWKSet, KeyUse, RSAKey}
import io.jsonwebtoken._
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import za.co.absa.loginsvc.model.User
import za.co.absa.loginsvc.rest.config.jwt.InMemoryKeyConfig
import za.co.absa.loginsvc.rest.config.provider.JwtConfigProvider
import za.co.absa.loginsvc.rest.model.{AccessToken, RefreshToken, Token}
import za.co.absa.loginsvc.rest.service.jwt.JWTService.{extractUserFrom, parseWithKeys}
import za.co.absa.loginsvc.rest.service.search.UserSearchService

import java.security.interfaces.RSAPublicKey
import java.security.{KeyPair, PublicKey}
import java.time.Instant
import java.util.Date
import java.util.concurrent.{ScheduledThreadPoolExecutor, ThreadFactory, TimeUnit}
import scala.collection.JavaConverters._
import scala.compat.java8.DurationConverters._
import scala.concurrent.duration.{Duration, FiniteDuration}

@Service
class JWTService @Autowired()(jwtConfigProvider: JwtConfigProvider, authSearchService: UserSearchService) {

  private val logger = LoggerFactory.getLogger(classOf[JWTService])
  private val scheduler = new ScheduledThreadPoolExecutor(3, new ThreadFactory {
    override def newThread(r: Runnable): Thread = {
      val t = new Thread(r)
      t.setDaemon(true)
      t
    }
  })

  private val jwtConfig = jwtConfigProvider.getJwtKeyConfig
  @volatile private var (primaryKeyPair: KeyPair, optionalKeyPair: Option[KeyPair]) = jwtConfig.keyPair()

  jwtConfig.keyRotationTime.foreach(scheduleSecretsRefresh)

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
      .signWith(primaryKeyPair.getPrivate)
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
      .signWith(primaryKeyPair.getPrivate)
      .compact()

    RefreshToken(tokenContent)
  }

  def refreshTokens(accessToken: AccessToken, refreshToken: RefreshToken): (AccessToken, RefreshToken) = {

    val keyList: List[PublicKey] = List(primaryKeyPair.getPublic) ++ optionalKeyPair.map(_.getPublic).toList

    val oldAccessJws: Option[Jws[Claims]] = parseWithKeys(
      accessToken,
      keyList,
      Token.TokenType.Access.toString,
      Some(jwtConfig.refreshExpTime)
    ) // checks requirements: type=access, signature, custom validity window

    if(oldAccessJws.isEmpty)
      throw new JwtException("Tokens are incompatible with current keys. Please request new Tokens!")

    val userFromOldAccessToken: User = extractUserFrom(oldAccessJws.get.getBody)

    val refreshClaims = parseWithKeys(
      refreshToken,
      keyList,
      Token.TokenType.Refresh.toString
    )

    if(refreshClaims.isEmpty)
      throw new JwtException("Tokens are incompatible with current keys. Please request new Tokens!")

    val userUpdatedDetails = {
      try {
        val searchedUser = authSearchService.searchUser(userFromOldAccessToken.name)
        val prefixedGroups = searchedUser.groups.intersect(userFromOldAccessToken.groups) // only keep groups that were in old token
        User(searchedUser.name, prefixedGroups, searchedUser.optionalAttributes)
      } catch {
        case _: Throwable => throw new UnsupportedJwtException(s"User ${userFromOldAccessToken.name} not found")
      }
    } // check if user still exists

    val refreshedAccessToken = generateAccessToken(userUpdatedDetails, isRefresh = true) // same process as with normal generation, but different msg

    // we are giving the original still-valid refreshToken back - potentially making room here to revoke or regenerate refreshTokens later
    (refreshedAccessToken, refreshToken)
  }

  def publicKeys: (PublicKey, Option[PublicKey]) = {
    val currentPublicKey = primaryKeyPair.getPublic
    val previousPublicKey = optionalKeyPair.map(_.getPublic)
    (currentPublicKey, previousPublicKey)
  }

  def publicKeyThumbprint: String = rsaPublicKey(primaryKeyPair.getPublic).getKeyID

  def jwks: JWKSet = {
    val currentJwk = rsaPublicKey(primaryKeyPair.getPublic)
    val previousJwk = optionalKeyPair.map(kp => rsaPublicKey(kp.getPublic))

    val jwkList = previousJwk match {
      case Some(previousJwk) => List[JWK](currentJwk, previousJwk)
      case None              => List[JWK](currentJwk)
    }

    new JWKSet(jwkList.asJava)
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

  private def rsaPublicKey(key: PublicKey): RSAKey = {
    key match {
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
          var (newPrimaryKeyPair, newOptionalKeyPair) = jwtConfig.keyPair()
          logger.info("Keys have been Refreshed")

          jwtConfig.keyLayOverTime.foreach { kl => {
            jwtConfig match {
              case _: InMemoryKeyConfig =>
                newOptionalKeyPair.foreach { tok =>
                  scheduleKeyLayOver(kl)
                  val temp = tok
                  newOptionalKeyPair = Some(newPrimaryKeyPair)
                  newPrimaryKeyPair = temp
                }
              case _ =>
            }
          }}

          jwtConfig.keyPhaseOutTime.foreach { kp => {
            jwtConfig match {
              case _: InMemoryKeyConfig =>
                scheduleKeyPhaseOut(kp + jwtConfig.keyLayOverTime.getOrElse(Duration.Zero))
              case _ =>
            }
          }}

          primaryKeyPair = newPrimaryKeyPair
          optionalKeyPair = newOptionalKeyPair
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

  private def scheduleKeyPhaseOut(phaseOutTime: FiniteDuration): Unit = {
    val scheduledFuture = scheduler.schedule(new Runnable {
      override def run(): Unit = {
        logger.info("Phasing out previous KeyPair.")
        optionalKeyPair = None
      }
    }, phaseOutTime.toMillis, TimeUnit.MILLISECONDS)
    Runtime.getRuntime.addShutdownHook(new Thread(() => {
      scheduledFuture.cancel(false)
      this.close()
    }))
  }

  private def scheduleKeyLayOver(layOverTime: FiniteDuration): Unit = {
    val scheduledFuture = scheduler.schedule(new Runnable {
      override def run(): Unit = {
        logger.info("Switching Signing key")
        optionalKeyPair.foreach { okp =>
          val temp = okp
          optionalKeyPair = Some(primaryKeyPair)
          primaryKeyPair = temp
        }
      }
    }, layOverTime.toMillis, TimeUnit.MILLISECONDS)
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

  def parseWithKeys(
    token: Token,
    keys: List[PublicKey],
    accessType: String,
    clock: Option[FiniteDuration] = None
  ): Option[Jws[Claims]] = {
    keys.flatMap { key =>
      try {
          val builder = Jwts.parserBuilder()
            .require("type", accessType)
            .setSigningKey(key)

        clock.foreach(time => builder.setClock(() => Date.from(Instant.now().minus(time.toJava))))

        Some(builder.build().parseClaimsJws(token.token))
      } catch {
        case e: MalformedJwtException =>
          throw e
        case e: ExpiredJwtException =>
          throw e
        case _: JwtException => None
      }
    }.headOption
  }
}
