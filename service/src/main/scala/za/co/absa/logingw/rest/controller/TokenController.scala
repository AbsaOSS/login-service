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

package za.co.absa.logingw.rest.controller

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.{HttpStatus, MediaType}
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.{GetMapping, PostMapping, RequestMapping, ResponseStatus, RestController}
import za.co.absa.logingw.model.User
import za.co.absa.logingw.rest.service.JWTService

import java.util.Base64
import java.util.concurrent.CompletableFuture
import scala.concurrent.Future


case class TokenWrapper(token: String) extends AnyVal

case class PublicKeyWrapper(key: String) extends AnyVal

@RestController
@RequestMapping(Array("/token"))
class TokenController @Autowired() (jwtService: JWTService) {

  import za.co.absa.logingw.utils.implicits._

  @PostMapping(
    path = Array("/generate"),
    produces = Array(MediaType.APPLICATION_JSON_VALUE)
  )
  @ResponseStatus(HttpStatus.OK)
  def generateToken(authentication: Authentication): CompletableFuture[TokenWrapper] = {
    val principal = authentication.getPrincipal.asInstanceOf[User]
    val jwt = jwtService.generateToken(principal)
    Future.successful(TokenWrapper(jwt))
  }

  @GetMapping(
    path = Array("/public-key"),
    produces = Array(MediaType.APPLICATION_JSON_VALUE)
  )
  @ResponseStatus(HttpStatus.OK)
  def getPublicKey(): CompletableFuture[PublicKeyWrapper] = {
    val publicKey = jwtService.publicKey
    val publicKeyBase64 = Base64.getEncoder.encodeToString(publicKey.getEncoded)

    Future.successful(PublicKeyWrapper(publicKeyBase64))
  }

}
