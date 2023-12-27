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

import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder
import za.co.absa.loginclient.publicKeyRetrieval.model.PublicKey
import za.co.absa.loginclient.publicKeyRetrieval.client.PublicKeyRetrievalClient
import za.co.absa.loginclient.tokenRetrieval.model.{AccessToken, RefreshToken}

import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import java.security.interfaces.RSAPublicKey
import java.util.Base64

object DecoderProvider {

  /**
   * Retrieves a NimbusJwtDecoder instance using the provided public key string.
   * This method creates and returns a NimbusJwtDecoder instance using the specified public key string.
   *
   * @param publicKeyString The string representation of the public key used for JWT decoding.
   * @return A NimbusJwtDecoder instance initialized with the provided public key string.
   */
  def getDecoder(publicKeyString: String): NimbusJwtDecoder = {
    val publicKeyBytes = Base64.getDecoder.decode(publicKeyString)
    val publicKeySpec = new X509EncodedKeySpec(publicKeyBytes)
    val encodedPublicKey = KeyFactory.getInstance("RSA").generatePublic(publicKeySpec).asInstanceOf[RSAPublicKey]
    NimbusJwtDecoder.withPublicKey(encodedPublicKey).build()
  }

  /**
   * Retrieves a NimbusJwtDecoder instance using the provided PublicKey object.
   * This method creates and returns a NimbusJwtDecoder instance using the specified PublicKey object.
   *
   * @param publicKey The PublicKey object used for JWT decoding.
   * @return A NimbusJwtDecoder instance initialized with the provided PublicKey object.
   */
  def getDecoder(publicKey: PublicKey): NimbusJwtDecoder = {
    getDecoder(publicKey.token)
  }

  /**
   * Retrieves a NimbusJwtDecoder instance by fetching the public key from the specified host url.
   * This method creates and returns a NimbusJwtDecoder instance with the retrieved public key.
   *
   * @param host The URL from which the public key will be fetched.
   * @return A NimbusJwtDecoder instance initialized with the public key fetched from the URL.
   */
  def getDecoderFromURL(host: String): NimbusJwtDecoder = {
    val publicKeyRetriever = PublicKeyRetrievalClient(host)
    getDecoder(publicKeyRetriever.getPublicKey)
  }

  /**
   * Decodes the provided token using the provided public key string.
   * This method decodes the provided token using the provided public key string and returns the decoded token.
   *
   * @param publicKey The string representation of the public key used for JWT decoding.
   * @param token     The token to be decoded.
   * @return A Jwt instance representing the decoded token.
   */
  def decodeToken(publicKey: String, token: String): Jwt = {
    val jwtDecoder = getDecoder(publicKey)
    try jwtDecoder.decode(token)
    catch {
      case e: Exception => throw new Exception("Invalid token")
    }
  }

  /**
   * Decodes the provided token using the provided NimbusJwtDecoder instance.
   * This method decodes the provided token using the provided NimbusJwtDecoder instance and returns the decoded token.
   *
   * @param decoder The NimbusJwtDecoder instance used for decoding.
   * @param token   The token to be decoded.
   * @return A Jwt instance representing the decoded token.
   */
  def decodeToken(decoder: NimbusJwtDecoder, token: String): Jwt = {
    try decoder.decode(token)
    catch {
      case e: Exception => throw new Exception("Invalid token")
    }
  }

  /**
   * Decodes the provided token using the public key fetched from the provided host url.
   * This method decodes the provided token using the public key fetched from the provided host url and returns the decoded token.
   *
   * @param host  The URL from which the public key will be fetched.
   * @param token The token to be decoded.
   * @return A Jwt instance representing the decoded token.
   */
  def decodeTokenFromURL(host: String, token: String): Jwt = {
    val jwtDecoder = getDecoderFromURL(host)
    try jwtDecoder.decode(token)
    catch {
      case e: Exception => throw new Exception("Invalid token")
    }
  }

  /**
   * Decodes the provided access token using the provided public key string.
   * This method decodes the provided access token using the provided public key string and returns the decoded token.
   *
   * @param publicKey   The string representation of the public key used for JWT decoding.
   * @param accessToken The access token to be decoded.
   * @return A Jwt instance representing the decoded token.
   */
  def decodeAccessToken(publicKey: PublicKey, accessToken: AccessToken): Jwt = {
    decodeToken(publicKey.token, accessToken.token)
  }

  /**
   * Decodes the provided access token using the provided NimbusJwtDecoder instance.
   * This method decodes the provided access token using the provided NimbusJwtDecoder instance and returns the decoded token.
   *
   * @param decoder     The NimbusJwtDecoder instance used for decoding.
   * @param accessToken The token to be decoded.
   * @return A Jwt instance representing the decoded token.
   */
  def decodeAccessToken(decoder: NimbusJwtDecoder, accessToken: AccessToken): Jwt = {
    decodeToken(decoder, accessToken.token)
  }

  /**
   * Decodes the provided refresh token using the public key fetched from the provided host url.
   * This method decodes the provided refresh token using the public key fetched from the provided host url and returns the decoded token.
   *
   * @param publicKey    The URL from which the public key will be fetched.
   * @param refreshToken The token to be decoded.
   * @return A Jwt instance representing the decoded token.
   */
  def decodeRefreshToken(publicKey: PublicKey, refreshToken: RefreshToken): Jwt = {
    decodeToken(publicKey.token, refreshToken.token)
  }

  /**
   * Decodes the provided refresh token using the provided NimbusJwtDecoder instance.
   * This method decodes the provided refresh token using the provided NimbusJwtDecoder instance and returns the decoded token.
   *
   * @param decoder      The NimbusJwtDecoder instance used for decoding.
   * @param refreshToken The token to be decoded.
   * @return A Jwt instance representing the decoded token.
   */
  def decodeRefreshToken(decoder: NimbusJwtDecoder, refreshToken: RefreshToken): Jwt = {
    decodeToken(decoder, refreshToken.token)
  }

  /**
   * Decodes and verifies the provided access token using the provided public key.
   * This method decodes and verifies the provided access token using the provided public key and returns the decoded token.
   *
   * @param publicKey   The string representation of the public key used for JWT decoding.
   * @param accessToken The token to be decoded.
   * @return A Jwt instance representing the decoded token.
   */
  def verifyAccessToken(publicKey: PublicKey, accessToken: AccessToken): Boolean = {
    try {
      val jwt = decodeAccessToken(publicKey, accessToken)
      ClaimsParser.verifyDecodedAccessToken(jwt)
    }
    catch {
      case e: Exception => false
    }
  }

  /**
   * Decodes and verifies the provided access token using the provided NimbusJwtDecoder instance.
   * This method decodes and verifies the provided access token using the provided NimbusJwtDecoder instance and returns the decoded token.
   *
   * @param decoder     The NimbusJwtDecoder instance used for decoding.
   * @param accessToken The token to be decoded.
   * @return A Jwt instance representing the decoded token.
   */
  def verifyAccessToken(decoder: NimbusJwtDecoder, accessToken: AccessToken): Boolean = {
    try {
      val jwt = decoder.decode(accessToken.token)
      ClaimsParser.verifyDecodedAccessToken(jwt)
    }
    catch {
      case e: Exception => false
    }
  }

  /**
   * Decodes and verifies the provided access token using the public key fetched from the provided host url.
   * This method decodes and verifies the provided access token using the public key fetched from the provided host url and returns the decoded token.
   *
   * @param host        The URL from which the public key will be fetched.
   * @param accessToken The token to be decoded.
   * @return A Jwt instance representing the decoded token.
   */
  def verifyAccessTokenFromURL(host: String, accessToken: AccessToken): Boolean = {
    try {
      val jwt = decodeTokenFromURL(host, accessToken.token)
      ClaimsParser.verifyDecodedAccessToken(jwt)
    }
    catch {
      case e: Exception => false
    }
  }
}
