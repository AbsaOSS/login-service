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
import za.co.absa.loginclient.tokenRetrieval.model.{AccessToken, RefreshToken}

import java.time.Instant
import java.util.Base64

class TokenVerificatorTest extends AnyFlatSpec with Matchers{

  private val publicKeyString = Base64.getEncoder.encodeToString(FakeTokens.keys.getPublic.getEncoded)
  private val decoder = JwtDecoderProvider.getDecoderFromPublicKeyString(publicKeyString)
  private val accessTokenVerificator = AccessTokenVerificator(decoder)
  private val refreshTokenVerificator = RefreshTokenVerificator(decoder)

  "Access Token" should "pass decoding" in {
    try {
      val token = AccessToken(FakeTokens.validAccessToken)
      accessTokenVerificator.decodeAndVerifyAccessToken(token)
      succeed
    } catch {
      case e: Throwable => fail("Access Token Decoding Failed")
    }
  }

  "Refresh Token" should "pass decoding" in {
    try {
      val token = RefreshToken(FakeTokens.validRefreshToken)
      refreshTokenVerificator.decodeAndVerifyRefreshToken(token)
      succeed
    } catch {
      case e: Throwable => fail("Access Token Decoding Failed")
    }
  }

  "Expired Access Token" should "fail decoding" in {
    try {
      val token = AccessToken(FakeTokens.invalidExpirationAccessToken)
      accessTokenVerificator.decodeAndVerifyAccessToken(token)
      fail("Access Token Decoding succeeded when it should have failed")
    } catch {
      case e: Throwable => succeed
    }
  }

  "Expired Refresh Token" should "fail decoding" in {
    try {
      val token = RefreshToken(FakeTokens.invalidExpirationRefreshToken)
      refreshTokenVerificator.decodeAndVerifyRefreshToken(token)
      fail("Access Token Decoding succeeded when it should have failed")
    } catch {
      case e: Throwable => succeed
    }
  }

  "Incorrectly signed Access Token" should "fail decoding" in {
    try {
      val token = AccessToken(FakeTokens.invalidSignatureAccessToken)
      accessTokenVerificator.decodeAndVerifyAccessToken(token)
      fail("Access Token Decoding succeeded when it should have failed")
    } catch {
      case e: Throwable => succeed
    }
  }

  "Incorrectly signed Refresh Token" should "fail decoding" in {
    try {
      val token = RefreshToken(FakeTokens.invalidSignatureRefreshToken)
      refreshTokenVerificator.decodeAndVerifyRefreshToken(token)
      fail("Access Token Decoding succeeded when it should have failed")
    } catch {
      case e: Throwable => succeed
    }
  }
}
