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

import io.jsonwebtoken.security.Keys
import io.jsonwebtoken.{Jwts, SignatureAlgorithm}
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import za.co.absa.loginsvc.model.User
import za.co.absa.loginsvc.rest.config.JwtConfig

import java.security.{KeyPair, PublicKey}
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date

@Service
class JWTService @Autowired()(jwtConfig: JwtConfig) {

  private val rsaKeyPair: KeyPair = Keys.keyPairFor(SignatureAlgorithm.valueOf(jwtConfig.algName))

  def generateToken(user: User): String = {
    import scala.collection.JavaConverters._


    val expiration = Date.from(
      Instant.now().plus(jwtConfig.expTime, ChronoUnit.HOURS)
    )
    val issuedAt = Date.from(Instant.now())
    // needs to be Java List/Array, otherwise incorrect claim is generated
    val groupsClaim = user.groups.asJava

    val jwtBuilderWithoutEmail = Jwts
      .builder()
      .setSubject(user.name)
      .setExpiration(expiration)
      .setIssuedAt(issuedAt)
      .claim("groups", groupsClaim)

    user
      .email
      .map(jwtBuilderWithoutEmail.claim("email", _))
      .getOrElse(jwtBuilderWithoutEmail)
      .signWith(rsaKeyPair.getPrivate)
      .compact()
  }

  def publicKey: PublicKey = rsaKeyPair.getPublic

}