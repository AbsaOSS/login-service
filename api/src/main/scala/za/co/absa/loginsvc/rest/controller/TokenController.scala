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

import io.swagger.v3.oas.annotations.enums.ParameterIn
import io.swagger.v3.oas.annotations.media.{Content, ExampleObject, Schema}
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.{Tag, Tags}
import io.swagger.v3.oas.annotations.{Operation, Parameter}
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.{HttpStatus, MediaType}
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation._
import org.springframework.web.server.ResponseStatusException
import za.co.absa.loginsvc.model.User
import za.co.absa.loginsvc.rest.model.{KerberosUserDetails, PublicKey, PublicKeySet, TokensWrapper}
import za.co.absa.loginsvc.rest.service.jwt.JWTService
import za.co.absa.loginsvc.utils.OptionUtils.ImplicitBuilderExt

import java.util.concurrent.CompletableFuture
import java.util.{Base64, Optional}
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration


@RestController
@RequestMapping(Array("/token"))
class TokenController @Autowired()(jwtService: JWTService) {

  private lazy val refreshExpDuration: FiniteDuration = jwtService.getConfiguredRefreshExpDuration

  import za.co.absa.loginsvc.utils.implicits._

  @Tags(Array(new Tag(name = "token")))
  @Operation(
    summary = "Generates access and refresh JWTs",
    description = """Generates access and refresh JWTs signed by the private key, verifiable by the public key available at /token/public-key. RSA256 is used.""",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "JWTs are retrieved in the response body",
        content = Array(new Content(
          schema = new Schema(implementation = classOf[TokensWrapper]),
          examples = Array(new ExampleObject(value = "{\n  \"token\": \"abcd123.efgh456.ijkl789\",\n  \"refresh\": \"ab12.cd34.ef56\"\n}")))
        )
      ),
      new ApiResponse(responseCode = "401", description = "Auth error",
        content = Array(new Content(
          schema = new Schema(implementation = classOf[String]), examples = Array(new ExampleObject(value = "Error: response status is 401")))
        ))
    )
  )
  @Parameter(in = ParameterIn.QUERY, name = "group-prefixes", schema = new Schema(implementation = classOf[String]), example = "pam-,dehdl-",
    description = "Prefixes of groups only to be returned in JWT user object (,-separated)")
  @PostMapping(
    path = Array("/generate"),
    produces = Array(MediaType.APPLICATION_JSON_VALUE)
  )
  @ResponseStatus(HttpStatus.OK)
  @SecurityRequirement(name = "basicAuth")
  @SecurityRequirement(name = "negotiate")
  def generateToken(authentication: Authentication, @RequestParam("group-prefixes") groupPrefixes: Optional[String]): CompletableFuture[TokensWrapper] = {

    val user: User = authentication.getPrincipal match {
      case u: User => u
      case k: KerberosUserDetails => k.getUser
      case _ => throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not authenticated or unknown principal type")
    }
    val groupPrefixesStrScala = groupPrefixes.toScalaOption

    val filteredGroupsUser = user.applyIfDefined(groupPrefixesStrScala) { (user: User, prefixesStr: String) =>
      val prefixes = prefixesStr.trim.split(',')
      user.filterGroupsByPrefixes(prefixes.toSet)
    }

    val accessJwt = jwtService.generateAccessToken(filteredGroupsUser)
    val refreshJwt = jwtService.generateRefreshToken(filteredGroupsUser)
    Future.successful(TokensWrapper.fromTokens(accessJwt, refreshJwt))
  }

  @Tags(Array(new Tag(name = "token")))
  @Operation(
    summary = "Refreshes access JWT",
    // note: further implementation, perhaps in https://github.com/AbsaOSS/login-service/issues/76, may issue new refresh tokens
    description = """Refreshed access JWT and (currently original) refresh JWTs signed by the private key, verifiable by the public key available at /token/public-key. RSA256 is used.""",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "JWTs are retrieved in the response body",
        content = Array(new Content(
          schema = new Schema(implementation = classOf[TokensWrapper]),
          examples = Array(new ExampleObject(value = "{\n  \"token\": \"abcd123.efgh456.ijkl789\",\n  \"refresh\": \"ab12.cd34.ef56\"\n}")))
        )
      ),
      new ApiResponse(responseCode = "401", description = "Understood the supplied tokens, but cannot refresh with those", // specific JWT expcetions
        content = Array(new Content(
          schema = new Schema(implementation = classOf[String]), examples = Array(new ExampleObject(value = "Error: Expired JWT")))
        )
      ),
      new ApiResponse(responseCode = "400", description = "Supplied tokens are invalid", // other JWT exceptions
        content = Array(new Content(
          schema = new Schema(implementation = classOf[String]), examples = Array(new ExampleObject(value = "Error: Malformed JWT")))
        )
      ),
    )
  )
  @PostMapping(
    path = Array("/refresh"),
    produces = Array(MediaType.APPLICATION_JSON_VALUE)
  )
  @ResponseStatus(HttpStatus.OK)
  def refreshToken(@RequestBody tokens: TokensWrapper): CompletableFuture[TokensWrapper] = {
    val (refreshedAccessToken, refreshedRefreshToken) = jwtService.refreshTokens(tokens.accessToken, tokens.refreshToken)
    Future.successful(TokensWrapper.fromTokens(refreshedAccessToken, refreshedRefreshToken))
  }

  @Tags(Array(new Tag(name = "token")))
  @Operation(
    summary = "Gives payload with the RSA256 public key",
    description = """Counterpart of /token/generate - JWT generated by /token/generate can be verified by the public token available through this endpoint.""",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Payload containing public key is returned",
        content = Array(new Content(
          schema = new Schema(implementation = classOf[PublicKey]),
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
  def getPublicKey(): CompletableFuture[PublicKey] = {
    val (publicKey, _) = jwtService.publicKeys
    val publicKeyBase64 = Base64.getEncoder.encodeToString(publicKey.getEncoded)
    Future.successful(PublicKey(publicKeyBase64))
  }

  @Tags(Array(new Tag(name = "token")))
  @Operation(
    summary = "Gives payload with the current and previously rotated RSA256 public key",
    description = """Alternative to /public-key - exposes current and previous public keys allowing users to verify a JWT after rotation.""",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Payload containing current and previous public keys is returned",
        content = Array(new Content(
          schema = new Schema(implementation = classOf[PublicKey]),
          examples = Array(new ExampleObject(value = "{\n \"keys\": [\n {\n \"key\": \"ABCDEFGH1234\"\n}\n]\n}")))
        )
      )
    )
  )
  @GetMapping(
    path = Array("/public-keys"),
    produces = Array(MediaType.APPLICATION_JSON_VALUE)
  )
  @ResponseStatus(HttpStatus.OK)
  def getAllPublicKeys(): CompletableFuture[PublicKeySet] = {
    val (primaryPublicKey, optionalPublicKey) = jwtService.publicKeys
    val currentPublicKey = PublicKey(Base64.getEncoder.encodeToString(primaryPublicKey.getEncoded))
    val previousPublicKey = optionalPublicKey.map(pk =>
      PublicKey(Base64.getEncoder.encodeToString(pk.getEncoded)))
    Future.successful(PublicKeySet(keys = currentPublicKey :: previousPublicKey.toList))
  }

  @Tags(Array(new Tag(name = "token")))
  @Operation(
    summary = "Gives payload with the RSA256 public key in JWKS format",
    description = "Returns the same information as /token/public-keys, but as a JSON Web Key Set",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Success", content = Array(new Content(examples = Array(new ExampleObject(value = """{"keys":[{"kty": "EC","crv": "P-256","x": "MKBCTNIcKUSDii11ySs3526iDZ8AiTo7Tu6KPAqv7D4","y": "4Etl6SRW2YiLUrN5vfvVHuhp7x8PxltmWWlbbM4IFyM","use": "enc","kid": "1"},{"kty": "RSA","n": "0vx7agoebGcQSuuPiLJXZptN9nndrQmbXEps2aiAFbWhM78LhWx4cbbfAAtVT86zwu1RK7aPFFxuhDR1L6tSoc_BJECPebWKRXjBZCiFV4n3oknjhMstn64tZ_2W-5JsGY4Hc5n9yBXArwl93lqt7_RN5w6Cf0h4QyQ5v-65YGjQR0_FDW2QvzqY368QQMicAtaSqzs8KJZgnYb9c7d0zgdAZHzu6qMQvRL5hajrn1n91CbOpbISD08qNLyrdkt-bFTWhAI4vMQFh6WeZu0fM4lFd2NcRwr3XPksINHaQ-G_xBniIqbw0Ls1jF44-csFCur-kEgU8awapJzKnqDKgw","e": "AQAB","alg": "RS256","kid": "2011-04-29"}]}"""))))),
    ))
  @GetMapping(
    path = Array("/public-key-jwks"),
    produces = Array(MediaType.APPLICATION_JSON_VALUE)
  )
  @ResponseStatus(HttpStatus.OK)
  def getPublicKeyJwks(): CompletableFuture[Map[String, AnyRef]] = {
    val jwk = jwtService.jwks

    import scala.collection.JavaConverters._
    Future.successful(jwk.toJSONObject(true).asScala.toMap)
  }
}

object TokenController {

}
