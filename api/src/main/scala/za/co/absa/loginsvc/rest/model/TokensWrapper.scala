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

package za.co.absa.loginsvc.rest.model

import com.fasterxml.jackson.annotation.{JsonIgnore, JsonProperty}
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode

case class TokensWrapper(
  @JsonProperty("token")
  @Schema(example = "abcd123.efgh456.ijkl789", requiredMode = RequiredMode.REQUIRED)
  token: String,
  @JsonProperty("refresh")
  @Schema(example = "ab12.cd34.ef56", requiredMode = RequiredMode.NOT_REQUIRED)
  refresh: String
) {
  def accessToken: AccessToken = AccessToken(token)
  def refreshToken: RefreshToken = RefreshToken(refresh)
}

object TokensWrapper {
  def fromTokens(accessToken: AccessToken, refreshToken: RefreshToken): TokensWrapper = {
    TokensWrapper(accessToken.token, refreshToken.token)
  }
}

case class AccessToken(
  token: String
) extends Token

case class RefreshToken(
  token: String,
) extends Token

trait Token {
  def token: String
}

object Token {
  object TokenType extends Enumeration {
    val Access = Value("access")
    val Refresh = Value("refresh")
  }
}
