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
import org.springframework.http.{HttpStatus, ResponseEntity}
import org.springframework.web.bind.annotation.{ControllerAdvice, ExceptionHandler, RestController}
import za.co.absa.loginsvc.rest.controller.ExperimentalFeaturesNotEnabledException
import za.co.absa.loginsvc.rest.model.RestMessage
import za.co.absa.loginsvc.rest.provider.ad.ldap.LdapConnectionException

@ControllerAdvice(annotations = Array(classOf[RestController]))
class RestResponseEntityExceptionHandler {

  private val logger = LoggerFactory.getLogger(classOf[RestResponseEntityExceptionHandler])

  @ExceptionHandler(value = Array(
    // specific subtypes of classOf[io.jsonwebtoken.JwtException]
    classOf[io.jsonwebtoken.security.SignatureException], // e.g. signature does not match
    classOf[io.jsonwebtoken.security.InvalidKeyException],
    classOf[io.jsonwebtoken.ExpiredJwtException]
  ))
  def handleInvalidSignatureException(exception: Exception): ResponseEntity[RestMessage] = {
    logger.error(exception.getMessage)
    ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(RestMessage(exception.getMessage))
  }

  @ExceptionHandler(value = Array(
    // LDAP connection errors (during Kerberos lookup)
    classOf[LdapConnectionException]
  ))
  def handleLdapConnectionException(exception: Exception): ResponseEntity[RestMessage] = {
    logger.error(exception.getMessage)
    ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(RestMessage(exception.getMessage))
  }

  @ExceptionHandler(value = Array(
    classOf[ExperimentalFeaturesNotEnabledException]
  ))
  def handleExperimentalFeaturesNotEnabledException(exception: Exception): ResponseEntity[RestMessage] = {
    logger.error(exception.getMessage)
    ResponseEntity.status(HttpStatus.CONFLICT).body(RestMessage(exception.getMessage))
  }


  @ExceptionHandler(value = Array(
    classOf[java.security.SignatureException], // other signature exceptions, e.g signature mismatch, malformed, ...
    classOf[io.jsonwebtoken.MalformedJwtException],
    classOf[io.jsonwebtoken.JwtException] // default handler for JwtExceptions (more specific defined above => 401)
  ))
  def handleSignatureProblemException(exception: Exception): ResponseEntity[RestMessage] = {
    logger.error(exception.getMessage)
    ResponseEntity.badRequest().body(RestMessage(exception.getMessage))
  }

  @ExceptionHandler(value = Array(classOf[IllegalArgumentException]))
  def handleIllegalArgumentException(exception: IllegalArgumentException): ResponseEntity[RestMessage] = {
    ResponseEntity.badRequest().body(RestMessage(exception.getMessage))
  }

}
