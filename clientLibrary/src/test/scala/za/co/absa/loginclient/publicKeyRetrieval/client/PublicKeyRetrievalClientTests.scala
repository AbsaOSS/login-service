package za.co.absa.loginclient.publicKeyRetrieval.client

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import za.co.absa.loginclient.publicKeyRetrieval.model.PublicKey

class PublicKeyRetrievalClientTests extends AnyFlatSpec with Matchers{

  private val dummyUri = "https://example.com"

  class testPublicKeyRetrievalClient extends PublicKeyRetrievalClient(dummyUri) {
    private val publicKeyUri = s"$dummyUri/token/public-key"
    private val publicKeyJson = """{"key": "mocked-public-key"}"""
    private val publicKeysUri = s"$dummyUri/token/public-keys"
    private val publicKeysJson =
      """{
        |  "keys": [
        |    { "key1": "public-key-1" },
        |    { "key2": "public-key-2" }
        |  ]
        |}""".stripMargin
    private val publicKeyJwkUri = s"$dummyUri/token/public-key-jwks"
    private val publicKeyJwkJson =
      """{
        |  "keys": [
        |    {
        |      "kty": "RSA",
        |      "kid": "test-key-id",
        |      "n": "test-modulus",
        |      "e": "test-exponent"
        |    }
        |  ]
        |}""".stripMargin

    override private[client] def fetchToken(issuerUri: String): String = {
      Map(
        publicKeyUri  -> publicKeyJson,
        publicKeysUri -> publicKeysJson,
        publicKeyJwkUri -> publicKeyJwkJson
      ).getOrElse(issuerUri, throw new IllegalArgumentException(s"Unexpected URI: $issuerUri"))
    }
  }

  "getPublicKey" should "return the expected PublicKey object" in {
    val testClient = new testPublicKeyRetrievalClient

    val result = testClient.getPublicKey

    result shouldBe PublicKey("mocked-public-key")
  }

  "getPublicKeys" should "return the expected set of PublicKey objects" in {
    val testClient = new testPublicKeyRetrievalClient

    val result = testClient.getPublicKeys

    result shouldBe Set(
      PublicKey("public-key-1"),
      PublicKey("public-key-2")
    )
  }

  "getPublicKeyJwk" should "return the expected PublicKey JWKSet object" in {
    val testClient = new testPublicKeyRetrievalClient

    val result = testClient.getPublicKeyJwk
    result.getKeyByKeyId("test-key-id").toString shouldBe
      "{\"kty\":\"RSA\",\"e\":\"test-exponent\",\"kid\":\"test-key-id\",\"n\":\"test-modulus\"}"
  }

}
