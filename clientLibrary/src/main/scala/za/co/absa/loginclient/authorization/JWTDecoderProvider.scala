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

package za.co.absa.loginclient.authorization

import org.slf4j.LoggerFactory
import org.springframework.security.oauth2.jwt.{Jwt, JwtDecoder, NimbusJwtDecoder}
import za.co.absa.loginclient.authorization.ClaimsParser.getAllClaims

import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.{Base64, Date}
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import scala.language.postfixOps
import za.co.absa.loginclient.tokenRetrieval.model.{AccessToken, PublicKey, Token}
import za.co.absa.loginclient.tokenRetrieval.service.RetrieveToken

import java.text.SimpleDateFormat
import scala.concurrent.duration.FiniteDuration

case class JWTDecoderProvider(publicKeyEndpoint : String, refreshPeriod : Option[FiniteDuration] = None) extends JwtDecoder {

  private val logger = LoggerFactory.getLogger(this.getClass)
  private val tokenRetrieval = RetrieveToken(publicKeyEndpoint)
  @volatile private var decoder: JwtDecoder = createDecoder(tokenRetrieval.getPublicKey)
  if (refreshPeriod.nonEmpty) scheduleKeyRefresh()

  private def scheduleKeyRefresh(): Unit = {
    val scheduler = Executors.newSingleThreadScheduledExecutor(r => {
      val t = new Thread(r)
      t.setDaemon(true)
      t
    })
    scheduler.scheduleAtFixedRate(() => {
      refreshDecoder()
    }, refreshPeriod.get.toMillis,
      refreshPeriod.get.toMillis,
      TimeUnit.MILLISECONDS
    )
  }

  private def refreshDecoder(): Unit = {
    try {
      decoder = createDecoder(tokenRetrieval.getPublicKey)
    }
    catch {
      case e: Throwable =>
        logger.error(s"Error occurred retrieving and decoding Public Key from $publicKeyEndpoint " +
          s"will attempt to retrieve Public Key again in $refreshPeriod seconds", e)
    }
  }

  private def createDecoder(publicKey: PublicKey): JwtDecoder = {
    val publicKeyBytes = Base64.getDecoder.decode(publicKey.token)
    val publicKeySpec = new X509EncodedKeySpec(publicKeyBytes)
    val encodedPublicKey = KeyFactory.getInstance("RSA").generatePublic(publicKeySpec).asInstanceOf[RSAPublicKey]
    NimbusJwtDecoder.withPublicKey(encodedPublicKey).build()
  }

  override def decode(token: String): Jwt = decoder.decode(token)

  def decode(token: Token): Jwt = decode(token.token)

  def verifyAccessToken(token: AccessToken): Map[String, Any] = {
    try {
      val jwt = decode(token)
      val claims = getAllClaims(jwt)
      val dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
      if(dateFormat.parse(claims("exp").toString).getTime < System.currentTimeMillis())
        throw new Exception("Token has expired")
      if(claims("type").toString != "access")
        throw new Exception("Token is not an access token")
      claims
    }
    catch {
      case e: Throwable =>
        logger.error(s"Error occurred verifying token", e)
        Map()
    }
  }
}
