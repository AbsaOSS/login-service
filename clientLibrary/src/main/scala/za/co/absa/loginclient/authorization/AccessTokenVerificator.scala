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

import org.springframework.security.oauth2.jwt.{Jwt, JwtDecoder}
import za.co.absa.loginclient.exceptions.LsJwtException
import za.co.absa.loginclient.tokenRetrieval.model.AccessToken

import java.time.Instant

case class AccessTokenVerificator(
  decoder: JwtDecoder
) {

  def decodeAndVerifyAccessToken(accessToken: AccessToken): Jwt = {

    val jwt = try decoder.decode(accessToken.token)
    catch {
      case e: Throwable => throw LsJwtException(s"Access Token Decoding Failed: ${e.getMessage}", e)
    }

    val verificationSuccess = verifyDecodedAccessToken(jwt)

    if (!verificationSuccess){
      throw LsJwtException("Access Token Verification Failed")
    }

    jwt
  }

  /**
   * Verifies that the JWT is a valid access token.
   * Checks that the token is not expired and that the type is access.
   *
   * @param jwt The JWT to parse.
   * @return True if the JWT is a valid access token, false otherwise.
   */
  private[authorization] def verifyDecodedAccessToken(jwt: Jwt): Boolean = {
    val exp = AccessTokenClaimsParser.getExpiration(jwt)
    val notExpired = exp.isAfter(Instant.now())

    val isAccessType = AccessTokenClaimsParser.isAccessTokenType(jwt)
    notExpired && isAccessType
  }
}
