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
import org.springframework.http.{HttpHeaders, HttpStatus, MediaType, ResponseCookie, ResponseEntity}
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation._
import za.co.absa.loginsvc.model.User
import za.co.absa.loginsvc.rest.controller.TokenController.{RefreshCookieName, extractRefreshTokenFromRequest, refreshResponseCookieFromRefreshToken, responseEntityWithRefreshCookieHeader}
import za.co.absa.loginsvc.rest.model.{AccessToken, PublicKey, RefreshToken}
import za.co.absa.loginsvc.rest.service.JWTService
import za.co.absa.loginsvc.utils.OptionUtils.ImplicitBuilderExt

import java.util.concurrent.CompletableFuture
import java.util.{Base64, Optional}
import javax.servlet.http.{Cookie, HttpServletRequest}
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
      new ApiResponse(responseCode = "200", description = "Access JWT is retrieved in the response body, the refresh JWT in Cookie 'refresh'.",
        content = Array(new Content(
          schema = new Schema(implementation = classOf[AccessToken]),
          examples = Array(new ExampleObject(value = "{\n  \"token\": \"abcd123.efgh456.ijkl789\"}")))
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
  @SecurityRequirement(name = "basicAuth")
  def generateToken(authentication: Authentication, @RequestParam("group-prefixes") groupPrefixes: Optional[String]): CompletableFuture[ResponseEntity[AccessToken]] = {
    val user = authentication.getPrincipal.asInstanceOf[User]
    val groupPrefixesStrScala = groupPrefixes.toScalaOption

    val filteredGroupsUser = user.applyIfDefined(groupPrefixesStrScala) { (user: User, prefixesStr: String) =>
      val prefixes = prefixesStr.trim.split(',')
      user.filterGroupsByPrefixes(prefixes.toSet)
    }

    val accessJwt = jwtService.generateAccessToken(filteredGroupsUser)
    val refreshJwt = jwtService.generateRefreshToken(filteredGroupsUser)

    Future.successful(
      responseEntityWithRefreshCookieHeader(refreshJwt, refreshExpDuration) {
        accessJwt
      }
    )
  }

  @Tags(Array(new Tag(name = "token")))
  @Operation(
    summary = "Refreshes access JWT",
    // note: further implementation, perhaps in https://github.com/AbsaOSS/login-service/issues/76, may issue new refresh tokens
    description = """Refreshed access JWT and (currently original) refresh JWTs signed by the private key, verifiable by the public key available at /token/public-key. RSA256 is used. Make sure that the refresh token is present in Cookies (refresh=ab.123.cd).""",
    responses = Array(
      new ApiResponse(responseCode = "200", description = "Access JWT is retrieved in the response body, updated refresh JWT in Cookie 'refresh'.",
        content = Array(new Content(
          schema = new Schema(implementation = classOf[AccessToken]),
          examples = Array(new ExampleObject(value = "{\n  \"token\": \"abcd123.efgh456.ijkl789\"}")))
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
  def refreshToken(@RequestBody accessToken: AccessToken, request: HttpServletRequest): CompletableFuture[ResponseEntity[AccessToken]] = {

    val response: Future[ResponseEntity[AccessToken]] = extractRefreshTokenFromRequest(request).map { refreshToken =>
      val (refreshedAccessToken, refreshedRefreshToken) = jwtService.refreshTokens(accessToken, refreshToken)

      Future.successful(
        responseEntityWithRefreshCookieHeader(refreshedRefreshToken, refreshExpDuration) {
          refreshedAccessToken
        }
      )

    }.getOrElse(
      Future.failed(new IllegalArgumentException("The expected refresh header not found, cannot refresh access token!"))
    )

    response
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
    val publicKey = jwtService.publicKey
    val publicKeyBase64 = Base64.getEncoder.encodeToString(publicKey.getEncoded)

    Future.successful(PublicKey(publicKeyBase64))
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

object TokenController {
  val RefreshCookieName = "refresh"

  def extractRefreshTokenFromRequest(request: HttpServletRequest): Option[RefreshToken] = {
    Option(request.getCookies()) // getCookies returns null if there are no cookies
      .getOrElse(Array.empty[Cookie])
      .find(_.getName == RefreshCookieName)
      .map(_.getValue)
      .map(RefreshToken)
  }

  def responseEntityWithRefreshCookieHeader[T](refreshToken: RefreshToken, refreshExpDuration: FiniteDuration)(body: T): ResponseEntity[T] = {
    val refreshCookie: ResponseCookie = refreshResponseCookieFromRefreshToken(refreshToken, refreshExpDuration)

    ResponseEntity
      .ok()
      .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
      .body(body)
  }

  def refreshResponseCookieFromRefreshToken(refreshToken: RefreshToken, refreshExpiryHint: FiniteDuration): ResponseCookie = {
   ResponseCookie.from(RefreshCookieName, refreshToken.token)
      .httpOnly(true)
      .secure(true)
      .path("/")
      .maxAge(refreshExpiryHint.toSeconds)
      .build()
  }

}
