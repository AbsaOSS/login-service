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
import io.jsonwebtoken.security.Keys
import io.jsonwebtoken._
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import za.co.absa.loginsvc.model.User
import za.co.absa.loginsvc.rest.config.provider.JwtConfigProvider
import za.co.absa.loginsvc.rest.model.Tokens
import za.co.absa.loginsvc.rest.service.JWTService.extractUserFrom
import za.co.absa.loginsvc.utils.OptionUtils.ImplicitBuilderExt

import java.security.interfaces.RSAPublicKey
import java.security.{KeyPair, PublicKey}
import java.time.Instant
import java.util.Date
import scala.collection.JavaConverters._
import scala.compat.java8.DurationConverters._

@Service
class JWTService @Autowired()(jwtConfigProvider: JwtConfigProvider) {

  private val jwtConfig = jwtConfigProvider.getJWTConfig
  private val rsaKeyPair: KeyPair = Keys.keyPairFor(SignatureAlgorithm.valueOf(jwtConfig.algName))

  def generateAccessToken(user: User): String = {
    import scala.collection.JavaConverters._

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
      .applyIfDefined(user.email) { (builder, value: String) => builder.claim("email", value) }
      .applyIfDefined(user.displayName) { (builder, value: String) => builder.claim("displayname", value) }
      .claim("type", Tokens.TokenType.Access.toString)
      .signWith(rsaKeyPair.getPrivate)
      .compact()
  }

  def generateRefreshToken(user: User): String = {
    val expiration = Date.from(
      Instant.now().plus(jwtConfig.refreshExpTime.toJava)
    )
    val issuedAt = Date.from(Instant.now())

    Jwts
      .builder()
      .setSubject(user.name)
      .setExpiration(expiration)
      .setIssuedAt(issuedAt)
      .claim("type", Tokens.TokenType.Refresh.toString)
      .signWith(rsaKeyPair.getPrivate)
      .compact()
  }

  def refreshToken(tokens: Tokens): String = {
    val oldAccessJws: Jws[Claims] = Jwts.parserBuilder()
      .require("type", Tokens.TokenType.Access.toString)
      .setSigningKey(rsaKeyPair.getPublic)
      .setClock(() => Date.from(Instant.now().minus(jwtConfig.refreshExpTime.toJava))) // allowing expired access token - up to refresh token validity window
      .build()
      .parseClaimsJws(tokens.token) // checks requirements: type=access, signature, custom validity window

    val userFromOldAccessToken = extractUserFrom(oldAccessJws.getBody)

    Jwts.parserBuilder()
      .require("type", Tokens.TokenType.Refresh.toString)
      .requireSubject(userFromOldAccessToken.name)
      .setSigningKey(rsaKeyPair.getPublic)
      .build()
      .parseClaimsJws(tokens.refresh) // checks username, validity, and signature.

    generateAccessToken(userFromOldAccessToken) // same process as with normal generation
  }

  def publicKey: PublicKey = rsaKeyPair.getPublic

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
}

object JWTService {

  def extractUserFrom(claims: Claims): User = {
    val name = claims.getSubject
    val groups = claims.get("groups", classOf[java.util.List[String]]).asScala
    val email = Option(claims.get("email", classOf[String]))
    val displayName = Option(claims.get("displayname", classOf[String]))

    User(name, email, displayName, groups)
  }
}
