package za.co.absa.loginclient.tokenRetrieval.client

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import za.co.absa.loginclient.tokenRetrieval.model.{AccessToken, RefreshToken}

class TokenRetrievalClientTest extends AnyFlatSpec with Matchers{

  private val dummyUri = "https://example.com"
  private val dummyUser = "exampleUser"
  private val dummyPassword = "examplePassword"
  private val dummyGroups = List()

  class testTokenRetrievalClient extends TokenRetrievalClient(dummyUri) {
    override private[client] def fetchToken(issuerUri: String, username: String, password: String) =
      """{
        |  "token": "mock-access-token",
        |  "refresh": "mock-refresh-token"
        |}""".stripMargin
  }

  "fetchAccessAndRefreshToken" should "return expected tokens" in {

    val testClient = new testTokenRetrievalClient

    val (accessResult, refreshResult) = testClient.fetchAccessAndRefreshToken(dummyUser, dummyPassword, dummyGroups)
    accessResult shouldBe AccessToken("mock-access-token")
    refreshResult shouldBe RefreshToken("mock-refresh-token")
  }
}
