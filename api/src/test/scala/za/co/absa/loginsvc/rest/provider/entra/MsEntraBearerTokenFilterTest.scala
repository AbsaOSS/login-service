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

import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.{mock, when}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.springframework.mock.web.{MockFilterChain, MockHttpServletRequest, MockHttpServletResponse}
import org.springframework.security.core.context.SecurityContextHolder
import za.co.absa.loginsvc.model.User

import javax.servlet.http.HttpServletResponse
import scala.util.{Failure, Success}

class MsEntraBearerTokenFilterTest extends AnyFlatSpec with Matchers with BeforeAndAfterEach {

  private val fakeUser = User("user@example.com", Seq("group1", "group2"), Map.empty)

  private val mockValidator = mock(classOf[MsEntraTokenValidator])
  private val filter = new MsEntraBearerTokenFilter(mockValidator)

  override def beforeEach(): Unit = {
    SecurityContextHolder.clearContext()
  }

  override def afterEach(): Unit = {
    SecurityContextHolder.clearContext()
  }

  "MsEntraBearerTokenFilter" should "authenticate and pass through on a valid Bearer token" in {
    when(mockValidator.validate(anyString())).thenReturn(Success(fakeUser))

    val request = new MockHttpServletRequest()
    request.addHeader("Authorization", "Bearer valid.entra.token")
    val response = new MockHttpServletResponse()
    val chain = new MockFilterChain()

    filter.doFilter(request, response, chain)

    response.getStatus shouldBe HttpServletResponse.SC_OK
    val auth = SecurityContextHolder.getContext.getAuthentication
    auth should not be null
    auth.getPrincipal shouldBe fakeUser
    chain.getRequest should not be null // filter chain was called
  }

  it should "return 401 and not call filter chain on an invalid Bearer token" in {
    when(mockValidator.validate(anyString())).thenReturn(Failure(new Exception("Bad token")))

    val request = new MockHttpServletRequest()
    request.addHeader("Authorization", "Bearer invalid.token")
    val response = new MockHttpServletResponse()
    val chain = new MockFilterChain()

    filter.doFilter(request, response, chain)

    response.getStatus shouldBe HttpServletResponse.SC_UNAUTHORIZED
    response.getContentType shouldBe "application/json"
    response.getContentAsString should include("Invalid or expired Entra token")
    chain.getRequest shouldBe null // filter chain was NOT called
  }

  it should "pass through without calling the validator when no Authorization header is present" in {
    val request = new MockHttpServletRequest()
    val response = new MockHttpServletResponse()
    val chain = new MockFilterChain()

    filter.doFilter(request, response, chain)

    response.getStatus shouldBe HttpServletResponse.SC_OK
    SecurityContextHolder.getContext.getAuthentication shouldBe null
    chain.getRequest should not be null
  }

  it should "pass through without calling the validator when Authorization header is not a Bearer token" in {
    val request = new MockHttpServletRequest()
    request.addHeader("Authorization", "Basic dXNlcjpwYXNzd29yZA==")
    val response = new MockHttpServletResponse()
    val chain = new MockFilterChain()

    filter.doFilter(request, response, chain)

    response.getStatus shouldBe HttpServletResponse.SC_OK
    SecurityContextHolder.getContext.getAuthentication shouldBe null
    chain.getRequest should not be null
  }

  it should "skip validation and pass through when SecurityContext is already authenticated" in {
    when(mockValidator.validate(anyString())).thenReturn(Success(fakeUser))

    // Pre-populate the security context as if another filter already authenticated
    val preExistingAuth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
      "already-authenticated-user", "creds",
      new java.util.ArrayList[org.springframework.security.core.GrantedAuthority]()
    )
    SecurityContextHolder.getContext.setAuthentication(preExistingAuth)

    val request = new MockHttpServletRequest()
    request.addHeader("Authorization", "Bearer some.token")
    val response = new MockHttpServletResponse()
    val chain = new MockFilterChain()

    filter.doFilter(request, response, chain)

    // Authentication should remain the pre-existing one
    SecurityContextHolder.getContext.getAuthentication.getPrincipal shouldBe "already-authenticated-user"
    chain.getRequest should not be null
  }

  it should "populate groups as Spring authorities" in {
    when(mockValidator.validate(anyString())).thenReturn(Success(fakeUser))

    val request = new MockHttpServletRequest()
    request.addHeader("Authorization", "Bearer valid.entra.token")
    val response = new MockHttpServletResponse()
    val chain = new MockFilterChain()

    filter.doFilter(request, response, chain)

    import scala.collection.JavaConverters._
    val authorities = SecurityContextHolder.getContext.getAuthentication.getAuthorities.asScala.map(_.getAuthority)
    authorities should contain allOf ("group1", "group2")
  }
}
