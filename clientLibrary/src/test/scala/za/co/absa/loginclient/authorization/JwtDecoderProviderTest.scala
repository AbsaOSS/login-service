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

import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.springframework.security.oauth2.jwt.JwtDecoder

import java.security.interfaces.RSAPublicKey
import java.util.Base64

class JwtDecoderProviderTest extends AnyFlatSpec with Matchers{

  "getDecoderFromPublicKeyString" should "correctly generate a JwtDecoder from a valid RSA public key string" in {
    val keyPair = Keys.keyPairFor(SignatureAlgorithm.RS256)

    val publicKey: RSAPublicKey = keyPair.getPublic.asInstanceOf[RSAPublicKey]
    val publicKeyString = Base64.getEncoder.encodeToString(publicKey.getEncoded)

    val decoder: JwtDecoder = JwtDecoderProvider.getDecoderFromPublicKeyString(publicKeyString)

    decoder should not be null
  }

}
