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
import java.util.Base64
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import scala.language.postfixOps
import za.co.absa.loginclient.tokenRetrieval.model.PublicKey
import za.co.absa.loginclient.tokenRetrieval.service.retrieveToken

case class jwtDecoderProvider(publicKeyEndpoint : String, refreshPeriod : Option[Int] = None) extends JwtDecoder {

  private val logger = LoggerFactory.getLogger(this.getClass)
  private val tokenRetrieval = retrieveToken(publicKeyEndpoint)
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
    }, refreshPeriod.get,
      refreshPeriod.get,
      TimeUnit.SECONDS
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

  def verifyToken(token: String): Map[String, Any] = {
    try {
      val jwt = decode(token)
      getAllClaims(jwt)
    }
    catch {
      case e: Throwable =>
        logger.error(s"Error occurred verifying token", e)
        Map()
    }
  }

}
