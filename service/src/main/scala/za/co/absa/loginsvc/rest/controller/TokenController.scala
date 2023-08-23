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

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.{ArraySchema, Content, ExampleObject, Schema}
import io.swagger.v3.oas.annotations.media.Schema.RequiredMode
import io.swagger.v3.oas.annotations.responses.{ApiResponse, ApiResponses}
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.{Tag, Tags}
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.{HttpStatus, MediaType}
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation._
import za.co.absa.loginsvc.model.User
import za.co.absa.loginsvc.rest.service.JWTService

import java.util.Base64
import java.util.concurrent.CompletableFuture
import scala.concurrent.Future


case class TokenWrapper(
  @JsonProperty("token")
  @Schema(example = "abcd123.efgh456.ijkl789", requiredMode = RequiredMode.REQUIRED)
  token: String
) extends AnyVal

case class PublicKeyWrapper(
  @JsonProperty("key")
  @Schema(example = "ABCDEFGH1234", requiredMode = RequiredMode.REQUIRED)
  key: String
) extends AnyVal

@RestController
@RequestMapping(Array("/token"))
class TokenController @Autowired()(jwtService: JWTService) {

  import za.co.absa.loginsvc.utils.implicits._

  @Tags(Array(new Tag(name = "token")))
  @Operation(
    summary = "Generates a JWT",
    description = """Generates a JWT signed by the private key, verifiable by the public key available at /token/public-key. RSA256 is used.""",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "JWT is retrieved in the response body",
        content = Array(new Content(
          schema = new Schema(implementation = classOf[TokenWrapper]),
          examples = Array(new ExampleObject(value = "{\n  \"token\": \"abcd123.efgh456.ijkl789\"\n}")))
        )
      ),
      new ApiResponse(responseCode = "401", description = "Auth error",
        content = Array(new Content(
          schema = new Schema(implementation = classOf[String]), examples = Array(new ExampleObject(value = "Error: response status is 401")))
        ))
    )
  )
  @PostMapping(
    path = Array("/generate"),
    produces = Array(MediaType.APPLICATION_JSON_VALUE)
  )
  @ResponseStatus(HttpStatus.OK)
  @SecurityRequirement(name = "basicAuth")
  def generateToken(authentication: Authentication): CompletableFuture[TokenWrapper] = {
    val principal = authentication.getPrincipal.asInstanceOf[User]
    val jwt = jwtService.generateToken(principal)
    Future.successful(TokenWrapper(jwt))
  }

  @Tags(Array(new Tag(name = "token")))
  @Operation(
    summary = "Gives payload with the RSA256 public key",
    description = """Counterpart of /token/generate - JWT generated by /token/generate can be verified by the public token available through this endpoint.""",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Payload containing public key is returned",
        content = Array(new Content(
          schema = new Schema(implementation = classOf[PublicKeyWrapper]),
          examples = Array(new ExampleObject(value = "{\n  \"key\": \"ABCDEFGH1234\"\n}")))
        )
      )
    )
  )
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

  @Tags(Array(new Tag(name = "token")))
  @Operation(
    summary = "Gives payload with the RSA256 public key in JWKS format",
    description = "Returns the same information as /token/public-key, but as a JSON Web Key Set",
    responses = Array(
    new ApiResponse(responseCode = "200", description = "Success", content = Array(new Content(examples = Array(new ExampleObject(value = """{"keys":[{"kty": "EC","crv": "P-256","x": "MKBCTNIcKUSDii11ySs3526iDZ8AiTo7Tu6KPAqv7D4","y": "4Etl6SRW2YiLUrN5vfvVHuhp7x8PxltmWWlbbM4IFyM","use": "enc","kid": "1"},{"kty": "RSA","n": "0vx7agoebGcQSuuPiLJXZptN9nndrQmbXEps2aiAFbWhM78LhWx4cbbfAAtVT86zwu1RK7aPFFxuhDR1L6tSoc_BJECPebWKRXjBZCiFV4n3oknjhMstn64tZ_2W-5JsGY4Hc5n9yBXArwl93lqt7_RN5w6Cf0h4QyQ5v-65YGjQR0_FDW2QvzqY368QQMicAtaSqzs8KJZgnYb9c7d0zgdAZHzu6qMQvRL5hajrn1n91CbOpbISD08qNLyrdkt-bFTWhAI4vMQFh6WeZu0fM4lFd2NcRwr3XPksINHaQ-G_xBniIqbw0Ls1jF44-csFCur-kEgU8awapJzKnqDKgw","e": "AQAB","alg": "RS256","kid": "2011-04-29"}]}"""))))),
  ))
  @GetMapping(
    path = Array("/public-key-jwks"),
    produces = Array(MediaType.APPLICATION_JSON_VALUE)
  )
  @ResponseStatus(HttpStatus.OK)
  def getPublicKeyJwks(): CompletableFuture[Map[String, AnyRef]] = {
    val jwks = jwtService.jwks

    import scala.collection.JavaConverters._
    Future.successful(jwks.toJSONObject(true).asScala.toMap)
  }
}
