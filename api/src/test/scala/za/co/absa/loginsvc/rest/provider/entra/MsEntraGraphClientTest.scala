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

package za.co.absa.loginsvc.rest.provider.entra

import com.sun.net.httpserver.{HttpExchange, HttpHandler, HttpServer}
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import za.co.absa.loginsvc.rest.config.auth.MsEntraConfig

import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

class MsEntraGraphClientTest extends AnyFlatSpec with Matchers with BeforeAndAfterAll with BeforeAndAfterEach {

  @volatile private var tokenStatus = 200
  @volatile private var tokenResponseBody = """{"access_token":"graph-token"}"""
  @volatile private var graphStatus = 200
  @volatile private var graphResponseBody = """{"onPremisesSamAccountName":"UN123XY","onPremisesDomainName":"corp.dsarena.com"}"""
  @volatile private var lastTokenRequestBody = ""
  @volatile private var lastGraphAuthorization = ""
  @volatile private var lastGraphPath = ""
  @volatile private var lastGraphRawPath = ""
  @volatile private var lastGraphQuery = ""

  private var server: HttpServer = _
  private var baseUrl: String = _

  override protected def beforeAll(): Unit = {
    super.beforeAll()
    server = HttpServer.create(new InetSocketAddress(0), 0)
    server.createContext("/token", new HttpHandler {
      override def handle(exchange: HttpExchange): Unit = {
        lastTokenRequestBody = new String(exchange.getRequestBody.readAllBytes(), StandardCharsets.UTF_8)
        respond(exchange, tokenStatus, tokenResponseBody)
      }
    })
    server.createContext("/v1.0/users", new HttpHandler {
      override def handle(exchange: HttpExchange): Unit = {
        lastGraphAuthorization = Option(exchange.getRequestHeaders.getFirst("Authorization")).getOrElse("")
        lastGraphPath = exchange.getRequestURI.getPath
        lastGraphRawPath = exchange.getRequestURI.getRawPath
        lastGraphQuery = Option(exchange.getRequestURI.getRawQuery).getOrElse("")
        respond(exchange, graphStatus, graphResponseBody)
      }
    })
    server.start()
    baseUrl = s"http://127.0.0.1:${server.getAddress.getPort}"
  }

  override protected def afterAll(): Unit = {
    if (server != null) server.stop(0)
    super.afterAll()
  }

  override protected def beforeEach(): Unit = {
    tokenStatus = 200
    tokenResponseBody = """{"access_token":"graph-token"}"""
    graphStatus = 200
    graphResponseBody = """{"onPremisesSamAccountName":"UN123XY","onPremisesDomainName":"corp.dsarena.com"}"""
    lastTokenRequestBody = ""
    lastGraphAuthorization = ""
    lastGraphPath = ""
    lastGraphRawPath = ""
    lastGraphQuery = ""
    super.beforeEach()
  }

  private def respond(exchange: HttpExchange, status: Int, body: String): Unit = {
    val bytes = body.getBytes(StandardCharsets.UTF_8)
    exchange.sendResponseHeaders(status, bytes.length.toLong)
    val os = exchange.getResponseBody
    try os.write(bytes) finally os.close()
  }

  private def client(
    secret: Option[String] = Some("test-secret"),
    domains: Option[Map[String, String]] = Some(Map("corp.dsarena.com" -> "CORP"))
  ): MsEntraGraphClient =
    new MsEntraGraphClient(
      MsEntraConfig(
        tenantId = "tenant-id",
        clientId = "client-id",
        clientSecret = secret,
        audiences = Nil,
        domains = domains,
        order = 1,
        attributes = None
      ),
      tokenEndpointOverride = Some(s"$baseUrl/token"),
      graphUsersBaseUrlOverride = Some(s"$baseUrl/v1.0/users")
    )

  "MsEntraGraphClient" should "return a lowercase samAccountName without the domain prefix" in {
    client().resolveUsername("john.smith@example.com") shouldBe Some("un123xy")
    lastTokenRequestBody should include("grant_type=client_credentials")
    lastTokenRequestBody should include("client_id=client-id")
    lastTokenRequestBody should include("client_secret=test-secret")
    lastGraphAuthorization shouldBe "Bearer graph-token"
    lastGraphPath shouldBe "/v1.0/users/john.smith@example.com"
    lastGraphRawPath shouldBe "/v1.0/users/john.smith%40example.com"
    lastGraphQuery shouldBe "$select=onPremisesSamAccountName,onPremisesDomainName"
  }

  it should "return None when the user's domain is not in the configured allow-list" in {
    graphResponseBody = """{"onPremisesSamAccountName":"UN123XY","onPremisesDomainName":"unknown.domain"}"""

    client().resolveUsername("john.smith@example.com") shouldBe None
  }

  it should "return None when Graph does not provide on-premises attributes" in {
    graphResponseBody = """{"displayName":"John Smith"}"""

    client().resolveUsername("john.smith@example.com") shouldBe None
  }

  it should "return None when the token endpoint response has no access token" in {
    tokenResponseBody = """{"token_type":"Bearer"}"""

    client().resolveUsername("john.smith@example.com") shouldBe None
  }

  it should "return None when the Graph API responds with an error" in {
    graphStatus = 500
    graphResponseBody = """{"error":"server exploded"}"""

    client().resolveUsername("john.smith@example.com") shouldBe None
  }

  it should "return None when the client secret is missing" in {
    client(secret = None).resolveUsername("john.smith@example.com") shouldBe None
  }

  it should "allow direct testing of the mapped username helper" in {
    client().resolveMappedUsername("UN123XY", "corp.dsarena.com", "john.smith@example.com") shouldBe Some("un123xy")
  }
}
