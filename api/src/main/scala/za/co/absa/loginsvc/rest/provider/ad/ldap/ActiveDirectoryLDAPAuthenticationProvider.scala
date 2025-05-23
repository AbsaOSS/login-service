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

package za.co.absa.loginsvc.rest.provider.ad.ldap

import org.slf4j.LoggerFactory
import org.springframework.ldap.core.DirContextOperations
import org.springframework.security.authentication.{AuthenticationProvider, BadCredentialsException, UsernamePasswordAuthenticationToken}
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.{Authentication, GrantedAuthority}
import org.springframework.security.ldap.authentication.ad.{ActiveDirectoryLdapAuthenticationProvider => SpringSecurityActiveDirectoryLdapAuthenticationProvider}
import org.springframework.security.ldap.userdetails.LdapUserDetailsMapper
import za.co.absa.loginsvc.model.User
import za.co.absa.loginsvc.rest.config.auth.ActiveDirectoryLDAPConfig

import java.util
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}
import scala.util.{Failure, Success, Try}

/**
 * Enhances ActiveDirectoryLdapAuthenticationProvider from Spring Security LDAP library
 * with additional user information (email)
 * and conforms authenticated user to [[za.co.absa.loginsvc.model.User]] class.
 * */
class ActiveDirectoryLDAPAuthenticationProvider(config: ActiveDirectoryLDAPConfig) extends AuthenticationProvider {
  private val logger = LoggerFactory.getLogger(classOf[ActiveDirectoryLDAPAuthenticationProvider])
  logger.debug(s"ActiveDirectoryLDAPAuthenticationProvider init")

  private val baseImplementation = createAuthenticationProvider

  override def authenticate(authentication: Authentication): Authentication = {
    val username = authentication.getName
    logger.info(s"Login of user $username via LDAP")

    val fromBase = config.ldapRetry
      .fold(singleAttemptAuth(authentication))(x =>
        retryAuthAsync(x.attempts, x.delayMs, authentication))

    val fromBasePrincipal = fromBase.getPrincipal.asInstanceOf[UserDetailsWithExtras]
    val principal = User(
      fromBasePrincipal.getUsername,
      fromBasePrincipal.getAuthorities.asScala.map(_.getAuthority).toSeq,
      fromBasePrincipal.extraAttributes
    )

    val token = new UsernamePasswordAuthenticationToken(principal, fromBasePrincipal.getPassword, fromBasePrincipal.getAuthorities)
    logger.info(s"LDAP-based: Login of user $username - ok") // no throw until this point
    token
  }

  override def supports(authentication: Class[_]): Boolean = baseImplementation.supports(authentication)

  private def singleAttemptAuth(authentication: Authentication): Authentication = {
    try {
      baseImplementation.authenticate(authentication)
    } catch {
      case bc: BadCredentialsException =>
        logger.error(s"Login of user ${authentication.getName}: ${bc.getMessage}", bc)
        throw new BadCredentialsException(bc.getMessage)

      case re: RuntimeException =>
        logger.error(s"Login of user ${authentication.getName}: ${re.getMessage}", re)
        re.printStackTrace()
        throw re
    }
  }

  private def retryAuthAsync(attempts: Int, delayMs: Int, authentication: Authentication): Authentication = {
    def attempt(n: Int): Future[Authentication] = Future {
      Try(baseImplementation.authenticate(authentication)) match {
        case Success(auth) => auth
        case Failure(ex) if isRetryableException(ex) && n <= attempts =>
          logger.error(s"AD authentication failed on attempt $n: ${ex.getMessage}. Retrying in ${delayMs * n}ms...")
          Thread.sleep(delayMs * n)
          Await.result(attempt(n + 1), Duration.Inf)
        case Failure(ex: BadCredentialsException) =>
          logger.error(s"Login of user ${authentication.getName}: ${ex.getMessage}", ex)
          throw new BadCredentialsException(ex.getMessage)
        case Failure(ex) =>
          logger.error(s"Login of user ${authentication.getName} failed after $n attempts: ${ex.getMessage}", ex)
          ex.printStackTrace()
          throw ex
      }
    }
    Await.result(attempt(1), Duration.Inf)
  }

  private def isRetryableException(ex: Throwable): Boolean = {
    ex match {
      case _: BadCredentialsException => false
      case _ => true
    }
  }

  private[ldap] def createAuthenticationProvider: AuthenticationProvider = {
    val impl = new SpringSecurityActiveDirectoryLdapAuthenticationProvider(config.domain, config.url)

    impl.setSearchFilter(config.searchFilter)
    impl.setUserDetailsContextMapper(new LDAPUserDetailsContextMapperWithOptions(config.attributes.getOrElse(Map.empty)))

    impl
  }

  private[ldap] case class UserDetailsWithExtras(userDetails: UserDetails, extraAttributes: Map[String, Option[AnyRef]]) extends UserDetails {
    override def getAuthorities: util.Collection[_ <: GrantedAuthority] = userDetails.getAuthorities
    override def getPassword: String = userDetails.getPassword
    override def getUsername: String = userDetails.getUsername
    override def isAccountNonExpired: Boolean = userDetails.isAccountNonExpired
    override def isAccountNonLocked: Boolean = userDetails.isAccountNonLocked
    override def isCredentialsNonExpired: Boolean = userDetails.isCredentialsNonExpired
    override def isEnabled: Boolean = userDetails.isEnabled
  }

  private class LDAPUserDetailsContextMapperWithOptions(attributes: Map[String, String]) extends LdapUserDetailsMapper {

    override def mapUserFromContext(
                                     ctx: DirContextOperations,
                                     username: String,
                                     authorities: util.Collection[_ <: GrantedAuthority]
                                   ): UserDetails = {
      val fromBase = super.mapUserFromContext(ctx, username, authorities)
      val extraAttributes = attributes.map { case (fieldName, claimName) =>
        val value = Option(ctx.getAttributes().get(fieldName)).map(_.get())
        claimName -> value
      }

      UserDetailsWithExtras(fromBase, extraAttributes)
    }
  }
}
