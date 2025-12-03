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

package za.co.absa.loginclient.tokenRetrieval.client

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import za.co.absa.loginclient.tokenRetrieval.model.{AccessToken, BasicAuth, RefreshToken}

class TokenRetrievalClientTest extends AnyFlatSpec with Matchers{

  private val dummyUri = "https://example.com"
  private val dummyUser = "exampleUser"
  private val dummyPassword = "examplePassword"
  private val dummyGroups = List()
  private val dummyCaseSensitive = false

  class testTokenRetrievalClient extends TokenRetrievalClient(dummyUri) {
    override private[client] def fetchToken(issuerUri: String, username: String, password: String) =
      """{
        |  "token": "mock-access-token",
        |  "refresh": "mock-refresh-token"
        |}""".stripMargin
  }

  "fetchAccessAndRefreshToken" should "return expected tokens" in {

    val testClient = new testTokenRetrievalClient
    val authMethod = BasicAuth(dummyUser, dummyPassword)

    val (accessResult, refreshResult) = testClient.fetchAccessAndRefreshToken(
      authMethod,
      dummyGroups,
      dummyCaseSensitive)
    accessResult shouldBe AccessToken("mock-access-token")
    refreshResult shouldBe RefreshToken("mock-refresh-token")
  }
}
