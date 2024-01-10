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

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import za.co.absa.loginclient.exceptions.LsJwtException
import za.co.absa.loginclient.tokenRetrieval.model.RefreshToken

import java.util.Base64

class RefreshTokenVerificatorTest extends AnyFlatSpec with Matchers{

  private val publicKeyString = Base64.getEncoder.encodeToString(FakeTokens.keys.getPublic.getEncoded)
  private val decoder = JwtDecoderProvider.getDecoderFromPublicKeyString(publicKeyString)
  private val refreshTokenVerificator = RefreshTokenVerificator(decoder)

  "Refresh Token" should "pass decoding" in {
    val token = RefreshToken(FakeTokens.validRefreshToken)
    refreshTokenVerificator.decodeAndVerifyRefreshToken(token)
  }

  "Incorrectly structured Refresh Token" should "fail decoding" in {
    val token = RefreshToken(FakeTokens.missingSubjectToken)
    val exception = the[LsJwtException] thrownBy {
      refreshTokenVerificator.decodeAndVerifyRefreshToken(token)
    }
    exception.getMessage shouldBe "Refresh Token Decoding Failed: An error occurred while attempting to decode the Jwt: Malformed payload"
  }

  "Expired Refresh Token" should "fail decoding" in {
    val token = RefreshToken(FakeTokens.invalidExpirationRefreshToken)
    val exception = the[LsJwtException] thrownBy {
      refreshTokenVerificator.decodeAndVerifyRefreshToken(token)
    }
    exception.getMessage shouldBe "Refresh Token Decoding Failed: An error occurred while attempting to decode the Jwt: expiresAt must be after issuedAt"
  }

  "Incorrectly signed Refresh Token" should "fail decoding" in {
    val token = RefreshToken(FakeTokens.invalidSignatureRefreshToken)
    val exception = the[LsJwtException] thrownBy {
      refreshTokenVerificator.decodeAndVerifyRefreshToken(token)
    }
    exception.getMessage shouldBe "Refresh Token Decoding Failed: An error occurred while attempting to decode the Jwt: Signed JWT rejected: Invalid signature"
  }

  "Access Token with missing Type" should "return an exception" in {
    val token = RefreshToken(FakeTokens.invalidTypeToken)
    val exception = the[LsJwtException] thrownBy {
      refreshTokenVerificator.decodeAndVerifyRefreshToken(token)
    }
    exception.getMessage shouldBe "Refresh Token Verification Failed"
  }
}
