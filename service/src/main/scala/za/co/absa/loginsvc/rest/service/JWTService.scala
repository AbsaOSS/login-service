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
import io.jsonwebtoken.{JwtBuilder, Jwts, SignatureAlgorithm}
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import za.co.absa.loginsvc.model.User
import za.co.absa.loginsvc.rest.config.provider.JwtConfigProvider
import za.co.absa.loginsvc.rest.service.JWTService.JwtBuilderExt
import za.co.absa.loginsvc.utils.OptionExt

import java.security.interfaces.RSAPublicKey
import java.security.{KeyPair, PublicKey}
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date

@Service
class JWTService @Autowired()(jwtConfigProvider: JwtConfigProvider) {

  private val jwtConfig = jwtConfigProvider.getJWTConfig
  private val rsaKeyPair: KeyPair = Keys.keyPairFor(SignatureAlgorithm.valueOf(jwtConfig.algName))
  private val kid: String = s"rsa-${jwtConfig.algName}-1"

  def generateToken(user: User): String = {
    import scala.collection.JavaConverters._


    val expiration = Date.from(
      Instant.now().plus(jwtConfig.expTime, ChronoUnit.HOURS)
    )
    val issuedAt = Date.from(Instant.now())
    // needs to be Java List/Array, otherwise incorrect claim is generated
    val groupsClaim = user.groups.asJava

    Jwts
      .builder()
      .setSubject(user.name)
      .setExpiration(expiration)
      .setIssuedAt(issuedAt)
      .claim("kid", kid)
      .claim("groups", groupsClaim)
      .applyIfDefined(user.email, (builder, value: String) => builder.claim("email", value))
      .applyIfDefined(user.displayName, (builder, value: String) => builder.claim("displayname", value))
      .signWith(rsaKeyPair.getPrivate)
      .compact()
  }

  def publicKey: PublicKey = rsaKeyPair.getPublic

  def jwks: JWKSet = {
    val jwk = publicKey match {
      case rsaKey: RSAPublicKey => new RSAKey.Builder(rsaKey)
        .keyUse(KeyUse.SIGNATURE)
        .algorithm(JWSAlgorithm.parse(jwtConfig.algName))
        .keyID(kid)
        .build()
      case _ => throw new IllegalArgumentException("Unsupported public key type")
    }
    new JWKSet(jwk).toPublicJWKSet
  }
}

object JWTService {
  implicit class JwtBuilderExt(val jwtBuilder: JwtBuilder) extends AnyVal {
    def applyIfDefined[T](opt: Option[T], fn: (JwtBuilder, T) => JwtBuilder): JwtBuilder = {
      OptionExt.applyIfDefined(jwtBuilder, opt, fn)
    }
  }
}
