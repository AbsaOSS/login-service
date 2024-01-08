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
import za.co.absa.loginclient.tokenRetrieval.model.RefreshToken

import java.time.Instant

case class RefreshTokenVerificator(
decoder: JwtDecoder
) {

  def decodeAndVerifyRefreshToken(refreshToken: RefreshToken): Jwt = {

    val jwt = try decoder.decode(refreshToken.token)
    catch {
      case e: Throwable => throw LsJwtException("Refresh Token Decoding Failed")
    }

    val verificationSuccess = verifyDecodedRefreshToken(jwt)

    if (!verificationSuccess) {
      throw LsJwtException("Refresh Token Verification Failed")
    }

    jwt
  }

  /**
   * Verifies that the JWT is a refresh token.
   * Checks that the token is not expired and that the type is refresh.
   *
   * @param jwt The JWT to parse.
   * @return True if the JWT is a valid refresh token, false otherwise.
   */
  private[authorization] def verifyDecodedRefreshToken(jwt: Jwt): Boolean = {
    val claims = RefreshTokenClaimsParser.getAllClaims(jwt)
    verifyDecodedRefreshToken(claims)
  }

  /**
   * Verifies that the JWT is a valid refresh token.
   * Checks that the token is not expired and that the type is refresh.
   *
   * @param claims The claims of the JWT to parse.
   * @return True if the JWT is a valid refresh token, false otherwise.
   */
  private[authorization] def verifyDecodedRefreshToken(claims: Map[String, Any]): Boolean = {
    val exp = Instant.parse(claims("exp").toString).getEpochSecond
    val notExpired = exp > Instant.now().getEpochSecond
    val isRefreshType = claims("type").toString == "refresh"
    notExpired && isRefreshType
  }
}
