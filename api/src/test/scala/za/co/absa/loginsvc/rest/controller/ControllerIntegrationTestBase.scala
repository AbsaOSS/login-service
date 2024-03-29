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

package za.co.absa.loginsvc.rest.controller

import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach, Suite}
import org.springframework.http.HttpHeaders
import org.springframework.security.core.Authentication
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.context.TestContextManager
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.{MockMvc, MvcResult, ResultActions, ResultMatcher}
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.{asyncDispatch, get, post}
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.{content, header, request, status}
import za.co.absa.loginsvc.utils.OptionUtils.ImplicitBuilderExt
import org.hamcrest.{BaseMatcher, Description}

import javax.servlet.http.Cookie


/**
 * A base for all integration tests of controllers.
 *
 * Makes spring-boot-test related functionality working with ScalaTest.
 *
 * Contains useful assertions for controllers returning CompletableFuture.
 */
trait ControllerIntegrationTestBase extends BeforeAndAfterAll with BeforeAndAfterEach { self: Suite =>

  var mockMvc: MockMvc

  protected def dummyMethodToTrickSpringTestsEventListener(): Unit = {}

  private val taskContextManager: TestContextManager = new TestContextManager(self.getClass)

  override def beforeAll(): Unit = {
    taskContextManager.prepareTestInstance(self)
    taskContextManager.beforeTestClass()
    super.beforeAll()
  }

  override def beforeEach(): Unit = {
    taskContextManager.beforeTestMethod(
      self,
      self.getClass.getMethod("dummyMethodToTrickSpringTestsEventListener")
    )
    super.beforeEach()
  }

  override def afterEach(): Unit = {
    super.afterEach()
    taskContextManager.afterTestMethod(
      self,
      self.getClass.getMethod("dummyMethodToTrickSpringTestsEventListener"),
      null
    )
  }

  override def afterAll(): Unit = {
    super.afterAll()
    taskContextManager.afterTestClass()
  }

  object AssertionsForEndpointWithCompletableFuture {

    // TODO add all other methods and enhance them with possible request body, headers, etc. when needed
    sealed trait RequestMethod
    case class Get(queryParams: Map[String, String] = Map.empty) extends RequestMethod
    case class Post(
      headers: Option[HttpHeaders] = None,
      cookies: Option[Seq[Cookie]] = None,
      body: Option[String] = None
    ) extends RequestMethod

    case class ContainingMatcher(needles: Set[String]) extends BaseMatcher[String] {
      override def matches(haystack: Any): Boolean = {
        needles.forall(haystack.toString.contains)
      }

      override def describeTo(description: Description): Unit =
        description.appendText(s"all of $needles are found in supplied string")
    }

    private def requestMethodToMockHttpServletRequestBuilder(
                                                              requestMethod: RequestMethod,
                                                              endpoint: String
                                                            ): MockHttpServletRequestBuilder = {
      requestMethod match {
        case Get(queryParams) => queryParams.foldLeft(get(endpoint)) {
          case (acc, queryParam) => acc.queryParam(queryParam._1, queryParam._2)
        }
        case Post(optHeaders, optCookies, optBody) => post(endpoint)
          .applyIfDefined(optHeaders) { (builder, headers: HttpHeaders) => builder.headers(headers) }
          .applyIfDefined(optCookies) { (builder, cookies: Seq[Cookie]) => builder.cookie(cookies: _*) }
          .applyIfDefined(optBody) { (builder, body: String) =>
            builder.contentType("application/json").content(body)
          }
      }
    }

    def assertExpectedResponseFields(endpoint: String, requestMethod: RequestMethod)
      (expectedStatusCode: Int = 200, expectedJsonBody: String, expectedHeaderContaining: Option[(String, Set[String])] = None)
                                       (implicit auth: Option[Authentication] = None): ResultActions = {
      val action = requestMethodToMockHttpServletRequestBuilder(requestMethod, endpoint)
        .applyIfDefined(auth) { case (builder, definedAuth) => builder.`with`(authentication(definedAuth)) }

      val mvcResult = mockMvc
        .perform(action)
        .andExpect(request().asyncStarted())
        .andReturn()


      mockMvc
        .perform(asyncDispatch(mvcResult))
        .andExpect(status().is(expectedStatusCode))
        .andExpect(content().json(expectedJsonBody))
        .applyIfDefined(expectedHeaderContaining) { case (builder, (expectedHeaderName, expectedHeaderValSubstrings)) =>
          builder.andExpect(header().string(expectedHeaderName, ContainingMatcher(expectedHeaderValSubstrings)))
        }
    }

    def assertNotAuthenticatedFailure(endpoint: String, requestMethod: RequestMethod)
                                     (implicit auth: Authentication): ResultActions = {
      val action = requestMethodToMockHttpServletRequestBuilder(requestMethod, endpoint)

      mockMvc
        .perform(action.`with`(authentication(auth)))
        .andExpect(status().isUnauthorized())
    }

    def assertErrorStatusAndResultBodyJsonEquals(endpoint: String, requestMethod: RequestMethod, expectedStatus: Int, expectedJson: String)
      (auth: Option[Authentication] = None): ResultActions = {
      val action = requestMethodToMockHttpServletRequestBuilder(requestMethod, endpoint)
        .applyIfDefined(auth) { case (builder, definedAuth) => builder.`with`(authentication(definedAuth)) }

      mockMvc
        .perform(action)
        .andExpect(status().is(expectedStatus))
        .andExpect(content().json(expectedJson))
    }
  }

}
