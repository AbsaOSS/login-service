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

import com.google.common.cache.{CacheBuilder, CacheLoader, LoadingCache}
import org.slf4j.LoggerFactory
import za.co.absa.loginsvc.rest.config.auth.MsEntraConfig

import java.io.{DataOutputStream, InputStream}
import java.net.{HttpURLConnection, URL, URLEncoder}
import java.util.concurrent.TimeUnit
import scala.io.Source
import scala.util.{Failure, Success, Try}

/**
 * Resolves a username via the MS Graph API by looking up the user's on-premises SAM account name.
 *
 * Returns `Some("NETBIOS\\samAccountName")` when on-premises AD attributes are present,
 * or `None` to signal the caller to fall back to the UPN from the token.
 */
trait GraphUsernameResolver {
  def resolveUsername(upn: String): Option[String]
}

/**
 * MS Graph-based implementation of [[GraphUsernameResolver]].
 *
 * Acquires an access token for the Graph API via client credentials (clientId + clientSecret),
 * then queries `GET /v1.0/users/{upn}?$select=onPremisesSamAccountName,onPremisesDomainName`.
 * The DNS domain name is mapped to a NetBIOS name via the `domains` config map and the result
 * is returned as `NETBIOS\samAccountName`.
 *
 * Falls back to `None` (i.e., use UPN) when:
 *  - the user has no on-premises AD attributes (e.g. cloud-only or external-tenant users)
 *  - the `onPremisesDomainName` is not in the `domains` map
 *  - any HTTP or parsing error occurs
 *
 * The Graph access token is cached for 50 minutes.
 *
 * @param config Entra config — must have `clientSecret` set; `domains` maps DNS domain → NetBIOS name.
 */
class MsEntraGraphClient(config: MsEntraConfig) extends GraphUsernameResolver {

  private val logger = LoggerFactory.getLogger(classOf[MsEntraGraphClient])

  private val tokenEndpoint =
    s"https://login.microsoftonline.com/${config.tenantId}/oauth2/v2.0/token"

  private val graphUsersBaseUrl = "https://graph.microsoft.com/v1.0/users"

  private val domainMap: Map[String, String] = config.domains.getOrElse(Map.empty)

  // Cache the Graph access token; expires well before the typical 1-hour token lifetime
  private val accessTokenCache: LoadingCache[String, String] =
    CacheBuilder.newBuilder()
      .expireAfterWrite(50, TimeUnit.MINUTES)
      .build(new CacheLoader[String, String] {
        override def load(key: String): String = fetchAccessToken()
      })

  override def resolveUsername(upn: String): Option[String] = {
    Try {
      val accessToken = accessTokenCache.get("token")
      val (samOpt, domainOpt) = queryGraphForUser(accessToken, upn)

      (samOpt.filter(_.nonEmpty), domainOpt.filter(_.nonEmpty)) match {
        case (Some(sam), Some(dnsDomain)) =>
          domainMap.get(dnsDomain) match {
            case Some(netbios) =>
              logger.debug(s"Resolved user '$upn' to '$netbios\\$sam' via Graph API")
              Some(s"$netbios\\$sam")
            case None =>
              logger.error(
                s"Unknown onPremisesDomainName '$dnsDomain' for user '$upn'. " +
                  "Add it to the 'domains' mapping in the Entra config. Falling back to UPN."
              )
              None
          }

        case _ =>
          logger.debug(s"User '$upn' has no on-premises AD attributes; using UPN as username")
          None
      }
    } match {
      case Success(result) => result
      case Failure(e) =>
        logger.warn(s"Graph API lookup failed for '$upn': ${e.getMessage}")
        None
    }
  }

  private def fetchAccessToken(): String = {
    val secret = config.clientSecret.getOrElse(
      throw new IllegalStateException("clientSecret is required to call the Graph API")
    )
    val body = Seq(
      "grant_type"    -> "client_credentials",
      "client_id"     -> config.clientId,
      "client_secret" -> secret,
      "scope"         -> "https://graph.microsoft.com/.default"
    ).map { case (k, v) => URLEncoder.encode(k, "UTF-8") + "=" + URLEncoder.encode(v, "UTF-8") }
      .mkString("&")

    val responseJson = httpPost(tokenEndpoint, body)
    parseJsonStringField(responseJson, "access_token")
      .getOrElse(throw new IllegalStateException("No access_token in token endpoint response"))
  }

  private def queryGraphForUser(accessToken: String, upn: String): (Option[String], Option[String]) = {
    val encodedUpn = URLEncoder.encode(upn, "UTF-8")
    val url = s"$graphUsersBaseUrl/$encodedUpn?$$select=onPremisesSamAccountName,onPremisesDomainName"
    val responseJson = httpGet(url, accessToken)
    val sam    = parseJsonStringField(responseJson, "onPremisesSamAccountName")
    val domain = parseJsonStringField(responseJson, "onPremisesDomainName")
    (sam, domain)
  }

  private def httpPost(urlStr: String, body: String): String = {
    val conn = new URL(urlStr).openConnection().asInstanceOf[HttpURLConnection]
    conn.setRequestMethod("POST")
    conn.setDoOutput(true)
    conn.setConnectTimeout(5000)
    conn.setReadTimeout(5000)
    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
    val bytes = body.getBytes("UTF-8")
    conn.setRequestProperty("Content-Length", bytes.length.toString)
    val out = new DataOutputStream(conn.getOutputStream)
    try { out.write(bytes) } finally { out.close() }
    readResponse(conn)
  }

  private def httpGet(urlStr: String, bearerToken: String): String = {
    val conn = new URL(urlStr).openConnection().asInstanceOf[HttpURLConnection]
    conn.setRequestMethod("GET")
    conn.setConnectTimeout(5000)
    conn.setReadTimeout(5000)
    conn.setRequestProperty("Authorization", s"Bearer $bearerToken")
    conn.setRequestProperty("ConsistencyLevel", "eventual")
    readResponse(conn)
  }

  private def readResponse(conn: HttpURLConnection): String = {
    val status = conn.getResponseCode
    val stream: InputStream =
      if (status >= 200 && status < 300) conn.getInputStream else conn.getErrorStream
    val body = Source.fromInputStream(stream, "UTF-8").mkString
    if (status >= 200 && status < 300) body
    else throw new RuntimeException(s"HTTP $status from ${conn.getURL.getHost}: $body")
  }

  /** Extracts a string-valued field from a flat JSON object, returning None if absent or null. */
  private def parseJsonStringField(json: String, fieldName: String): Option[String] = {
    val escapedName = java.util.regex.Pattern.quote(fieldName)
    val pattern = ("\"" + escapedName + "\"\\s*:\\s*\"([^\"]+)\"").r
    pattern.findFirstMatchIn(json).map(_.group(1))
  }
}
