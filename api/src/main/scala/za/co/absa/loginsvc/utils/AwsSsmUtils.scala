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

import software.amazon.awssdk.services.ssm.SsmClient
import software.amazon.awssdk.services.ssm.model._
import org.slf4j.LoggerFactory

object AwsSsmUtils extends AwsSsmUtils

class AwsSsmUtils extends SsmUtils {

  private val logger = LoggerFactory.getLogger(getClass)
  private[utils] def getSsm: SsmClient = SsmClient.builder().build()

  def getParameter(paramName: String, decryptIfSecure: Boolean): Option[String] = {
    val ssm = getSsm
    val request = GetParameterRequest.builder()
      .name(paramName)
      .withDecryption(decryptIfSecure)
      .build()
    try {
      logger.info(s"Attempting to fetch $paramName from AWS Ssm")
      val response = ssm.getParameter(request)
      logger.info(s"$paramName retrieved.")
      Some(response.parameter().value())
    }
    catch {
      case e: Throwable =>
        logger.warn(s"Error occurred retrieving $paramName from AWS Ssm", e)
        None
    }
  }
}

trait SsmUtils {
  def getParameter(paramName: String, decryptIfSecure: Boolean = true): Option[String]
}
