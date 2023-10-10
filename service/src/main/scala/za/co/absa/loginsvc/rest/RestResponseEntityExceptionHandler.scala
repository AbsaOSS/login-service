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

package za.co.absa.loginsvc.rest

import org.slf4j.LoggerFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.{ControllerAdvice, ExceptionHandler, RestController}
import za.co.absa.loginsvc.rest.model.RestMessage

@ControllerAdvice(annotations = Array(classOf[RestController]))
class RestResponseEntityExceptionHandler {

  private val logger = LoggerFactory.getLogger(classOf[RestResponseEntityExceptionHandler])

  @ExceptionHandler(value = Array(
    classOf[io.jsonwebtoken.security.SignatureException], // e.g. signature does not match
    classOf[java.security.SignatureException], // signature mismatch
    classOf[io.jsonwebtoken.ExpiredJwtException]
  ))
  def handleSignatureRelatedException(exception: Exception): ResponseEntity[RestMessage] = {
    logger.error(exception.getMessage)
    exception.printStackTrace()

    ResponseEntity.badRequest().body(RestMessage(exception.getMessage))
  }

  @ExceptionHandler(value = Array(classOf[IllegalArgumentException]))
  def handleIllegalArgumentException(exception: IllegalArgumentException): ResponseEntity[RestMessage] = {
    ResponseEntity.badRequest().body(RestMessage(exception.getMessage))
  }

}
