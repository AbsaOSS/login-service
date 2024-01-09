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
import za.co.absa.loginclient.tokenRetrieval.model.AccessToken

import java.util.Base64

class AccessTokenVerificatorTest extends AnyFlatSpec with Matchers{

  private val publicKeyString = Base64.getEncoder.encodeToString(FakeTokens.keys.getPublic.getEncoded)
  private val decoder = JwtDecoderProvider.getDecoderFromPublicKeyString(publicKeyString)
  private val accessTokenVerificator = AccessTokenVerificator(decoder)

  "Access Token" should "pass decoding" in {
    val token = AccessToken(FakeTokens.validAccessToken)
    accessTokenVerificator.decodeAndVerifyAccessToken(token)
  }

  "Expired Access Token" should "fail decoding" in {
    val token = AccessToken(FakeTokens.invalidExpirationAccessToken)
    val exception = the[LsJwtException] thrownBy {
      accessTokenVerificator.decodeAndVerifyAccessToken(token)
    }
    exception.getMessage shouldBe "Access Token Decoding Failed"
  }

  "Incorrectly signed Access Token" should "fail decoding" in {
    val token = AccessToken(FakeTokens.invalidSignatureAccessToken)
    val exception = the[LsJwtException] thrownBy {
      accessTokenVerificator.decodeAndVerifyAccessToken(token)
    }
    exception.getMessage shouldBe "Access Token Decoding Failed"
  }

  "Access Token with missing Type" should "return an exception" in {
    val token = AccessToken(FakeTokens.invalidTypeToken)
    val exception = the[LsJwtException] thrownBy {
      accessTokenVerificator.decodeAndVerifyAccessToken(token)
    }
    exception.getMessage shouldBe "Access Token Verification Failed"
  }
}
