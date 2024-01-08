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

import org.springframework.security.oauth2.jwt.{Jwt, JwtDecoder, NimbusJwtDecoder}
import za.co.absa.loginclient.publicKeyRetrieval.model.PublicKey
import za.co.absa.loginclient.tokenRetrieval.model.{AccessToken, RefreshToken}

import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

object JwtDecoderProvider {

  /**
   * Retrieves a JwtDecoder instance using the provided public key string.
   * This method creates and returns a JwtDecoder instance using the specified public key string.
   * Currently implemented by NimbusJwtDecoder.
   *
   * @param publicKeyString The string representation of the public key used for JWT decoding.
   * @return A JwtDecoder instance initialized with the provided public key string.
   */
  def getDecoderFromPublicKeyString(publicKeyString: String): JwtDecoder = {
    val publicKeyBytes = Base64.getDecoder.decode(publicKeyString)
    val publicKeySpec = new X509EncodedKeySpec(publicKeyBytes)
    val encodedPublicKey = KeyFactory.getInstance("RSA").generatePublic(publicKeySpec).asInstanceOf[RSAPublicKey]
    NimbusJwtDecoder.withPublicKey(encodedPublicKey).build()
  }

  /**
   * Retrieves a JwtDecoder instance using the provided PublicKey object.
   * This method creates and returns a JwtDecoder instance using the specified PublicKey object.
   * Currently implemented by NimbusJwtDecoder.
   *
   * @param publicKey The PublicKey object used for JWT decoding.
   * @return A JwtDecoder instance initialized with the provided PublicKey object.
   */
  def getDecoderFromPublicKey(publicKey: PublicKey): JwtDecoder = {
    getDecoderFromPublicKeyString(publicKey.token)
  }

  /**
   * Retrieves a JwtDecoder instance by fetching the public key from the specified host url.
   * This method creates and returns a JwtDecoder instance with the retrieved public key.
   * Currently implemented by NimbusJwtDecoder.withJwkSetUri(JWKS_PATH).
   *
   * @param host The URL from which the public key will be fetched.
   * @return A JwtDecoder instance initialized with the public key fetched from the URL.
   */
  def getDecoderFromURL(host: String): JwtDecoder = {
    NimbusJwtDecoder.withJwkSetUri(s"$host/token/public-key-jwks").build()
  }
}
