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

package za.co.absa.loginsvc.utils

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{mock, when}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import software.amazon.awssdk.services.ssm.SsmClient
import software.amazon.awssdk.services.ssm.model.{GetParameterRequest,GetParameterResponse, Parameter}

class AwsSsmUtilsTest extends AnyFlatSpec with Matchers {

  val json: String = """{
               |  "accountName": "ldap-user123",
               |  "accountPwd": "password456"
               |}""".stripMargin
  val fakeParameter: Parameter = Parameter.builder().value(json).build()
  val response: GetParameterResponse = GetParameterResponse.builder().parameter(fakeParameter).build()


  val definedSsmClient: SsmClient = mock(classOf[SsmClient])
  when(definedSsmClient.getParameter(any[GetParameterRequest])).thenAnswer(_ => response)

  class AwsSsmUtilsTestClass extends AwsSsmUtils {
    override private[utils] def getSsm = definedSsmClient
  }

  "AwsSsmUtils" should "Return a valid Option[String] when the Ssm client returns a value" in {
    val awsSsmUtilsTestClass = new AwsSsmUtilsTestClass
    val data = awsSsmUtilsTestClass.getParameter(
      "parameter",
      false
    )
    data.get shouldBe json
  }
}
