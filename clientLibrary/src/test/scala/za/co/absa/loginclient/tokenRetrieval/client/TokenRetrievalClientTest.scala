package za.co.absa.loginclient.tokenRetrieval.client

import org.mockito.ArgumentMatchers.{any, anyString}
import org.mockito.Mockito.{mock, when}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.springframework.http.{HttpEntity, HttpMethod, HttpStatus, ResponseEntity}
import org.springframework.security.kerberos.client.KerberosRestTemplate
import org.springframework.web.client.RestTemplate
import za.co.absa.loginclient.tokenRetrieval.model.{AccessToken, BasicAuth, KerberosAuth, RefreshToken}

class TokenRetrievalClientTest extends AnyFlatSpec with Matchers {

  private val dummyUri = "https://example.com"
  private val dummyUser = "exampleUser"
  private val dummyPassword = "examplePassword"
  private val dummyGroups = List("group1", "group2")
  private val dummyCaseSensitive = true

  class MockableTokenRetrievalClient(
    host: String,
    restTemplate: RestTemplate,
    kerberosRestTemplate: KerberosRestTemplate) extends TokenRetrievalClient(host) {

    override def createRestTemplate(): RestTemplate = restTemplate
    override def createKerberosRestTemplate(
      keyTabLocation: Option[String] = None,
      userPrincipal: Option[String] = None): KerberosRestTemplate = kerberosRestTemplate
  }

  "fetchAccessToken with BasicAuth" should "return access token" in {
    val mockRestTemplate = mock(classOf[RestTemplate])
    val mockKerberosTemplate = mock(classOf[KerberosRestTemplate])
    val tokenResponse = """{"token": "mock-access-token", "refresh": "mock-refresh-token"}"""

    when(mockRestTemplate.exchange(
      anyString(),
      any[HttpMethod],
      any[HttpEntity[String]],
      any[Class[String]]
    )).thenReturn(new ResponseEntity(tokenResponse, HttpStatus.OK))

    val testClient = new MockableTokenRetrievalClient(dummyUri, mockRestTemplate, mockKerberosTemplate)
    val authMethod = BasicAuth(dummyUser, dummyPassword)

    val result = testClient.fetchAccessToken(authMethod)

    result shouldBe AccessToken("mock-access-token")
  }

  "fetchRefreshToken with BasicAuth" should "return refresh token" in {
    val mockRestTemplate = mock(classOf[RestTemplate])
    val mockKerberosTemplate = mock(classOf[KerberosRestTemplate])
    val tokenResponse = """{"token": "mock-access-token", "refresh": "mock-refresh-token"}"""

    when(mockRestTemplate.exchange(
      anyString(),
      any[HttpMethod],
      any[HttpEntity[String]],
      any[Class[String]]
    )).thenReturn(new ResponseEntity(tokenResponse, HttpStatus.OK))

    val testClient = new MockableTokenRetrievalClient(dummyUri, mockRestTemplate, mockKerberosTemplate)
    val authMethod = BasicAuth(dummyUser, dummyPassword)

    val result = testClient.fetchRefreshToken(authMethod)

    result shouldBe RefreshToken("mock-refresh-token")
  }

  "fetchAccessAndRefreshToken with BasicAuth and groups" should "return both tokens with group parameters" in {
    val mockRestTemplate = mock(classOf[RestTemplate])
    val mockKerberosTemplate = mock(classOf[KerberosRestTemplate])
    val tokenResponse = """{"token": "mock-access-token", "refresh": "mock-refresh-token"}"""

    when(mockRestTemplate.exchange(
      anyString(),
      any[HttpMethod],
      any[HttpEntity[String]],
      any[Class[String]]
    )).thenReturn(new ResponseEntity(tokenResponse, HttpStatus.OK))

    val testClient = new MockableTokenRetrievalClient(dummyUri, mockRestTemplate, mockKerberosTemplate)
    val authMethod = BasicAuth(dummyUser, dummyPassword)

    val (accessResult, refreshResult) = testClient.fetchAccessAndRefreshToken(
      authMethod,
      dummyGroups,
      dummyCaseSensitive
    )

    accessResult shouldBe AccessToken("mock-access-token")
    refreshResult shouldBe RefreshToken("mock-refresh-token")
  }

  "fetchAccessAndRefreshToken with KerberosAuth" should "return access and refresh tokens" in {
    val mockRestTemplate = mock(classOf[RestTemplate])
    val mockKerberosTemplate = mock(classOf[KerberosRestTemplate])
    val tokenResponse = """{"token": "kerberos-access-token", "refresh": "kerberos-refresh-token"}"""

    when(mockKerberosTemplate.exchange(
      anyString(),
      any[HttpMethod],
      any[HttpEntity[String]],
      any[Class[String]]
    )).thenReturn(new ResponseEntity(tokenResponse, HttpStatus.OK))

    val testClient = new MockableTokenRetrievalClient(dummyUri, mockRestTemplate, mockKerberosTemplate)
    val authMethod = KerberosAuth(Some("/path/to/keytab"), Some("user@REALM"))

    val (accessResult, refreshResult) = testClient.fetchAccessAndRefreshToken(
      authMethod,
      dummyGroups,
      dummyCaseSensitive
    )

    accessResult shouldBe AccessToken("kerberos-access-token")
    refreshResult shouldBe RefreshToken("kerberos-refresh-token")
  }

  "refreshAccessToken" should "return new tokens" in {
    val mockRestTemplate = mock(classOf[RestTemplate])
    val mockKerberosTemplate = mock(classOf[KerberosRestTemplate])
    val tokenResponse = """{"token": "refreshed-token", "refresh": "old-refresh-token"}"""

    when(mockRestTemplate.exchange(
      anyString(),
      any[HttpMethod],
      any[HttpEntity[String]],
      any[Class[String]]
    )).thenReturn(new ResponseEntity(tokenResponse, HttpStatus.OK))

    val testClient = new MockableTokenRetrievalClient(dummyUri, mockRestTemplate, mockKerberosTemplate)

    val (newAccess, newRefresh) = testClient.refreshAccessToken(
      AccessToken("old-token"),
      RefreshToken("old-refresh")
    )

    newAccess shouldBe AccessToken("refreshed-token")
    newRefresh shouldBe RefreshToken("old-refresh-token")
  }
}