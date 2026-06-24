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

package za.co.absa.loginsvc.rest.service.search

import org.scalamock.scalatest.MockFactory
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import za.co.absa.loginsvc.model.User
import za.co.absa.loginsvc.rest.config.auth.{ActiveDirectoryLDAPConfig, LdapRetryConfig, LdapUserCredentialsConfig, ServiceAccountConfig}

import javax.naming.NamingEnumeration
import javax.naming.directory.{Attribute, Attributes, SearchResult}

class LdapUserRepositoryTest extends AnyFlatSpec with Matchers with MockFactory {

  private val integratedCfg = LdapUserCredentialsConfig("svc-ldap", "password")
  private val serviceAccountCfg = ServiceAccountConfig(
    "CN=%s,OU=Users,OU=Accounts,DC=domain,DC=subdomain,DC=com",
    Option(integratedCfg),
    None,
    None)
  private val ldapCfgNoRetries = ActiveDirectoryLDAPConfig(
    "some.domain.com",
    "ldaps://some.domain.com:636/",
    "SomeAccount",
    1,
    serviceAccountCfg,
    None,
    None,
    None)

  private val testUser: User = User(
    "user",
    Seq("group1", "group2"),
    Map("testAtt" -> None))

  private class TestLdapUserRepositoryEventuallySucceeding(config: ActiveDirectoryLDAPConfig)
    extends LdapUserRepository(config) {
    var counter: Int = 0
    override def contextSearch(username: String): List[User] = {
      config.ldapRetry.fold(List(testUser))(_ => {
        if(counter < 3) {
          counter += 1
          throw new RuntimeException("TestException")
        }
        else List(testUser)
      })
    }
  }

  "ContextSearch" should "only be called once when ldapRetry is None" in {
    val testLdapUserRepository = new TestLdapUserRepositoryEventuallySucceeding(ldapCfgNoRetries)
    val user = testLdapUserRepository.searchForUser(testUser.name)

    assert(testLdapUserRepository.counter == 0)
    assert(user.get == testUser)
  }

  "authenticate" should "be called 4 times before a successful attempt occurs" in {
    val retryConfig = LdapRetryConfig(4, 100)
    val ldapCfg = ldapCfgNoRetries.copy(ldapRetry = Some(retryConfig))

    val testLdapUserRepository = new TestLdapUserRepositoryEventuallySucceeding(ldapCfg)
    val user = testLdapUserRepository.searchForUser(testUser.name)

    assert(testLdapUserRepository.counter == 3)
    assert(user.get == testUser)
  }

  "authenticate" should "be fail after 2 retries" in {
    val retryConfig = LdapRetryConfig(2, 100)
    val ldapCfg = ldapCfgNoRetries.copy(ldapRetry = Some(retryConfig))

    val testLdapUserRepository = new TestLdapUserRepositoryEventuallySucceeding(ldapCfg)

    assertThrows[RuntimeException] {
      testLdapUserRepository.searchForUser(testUser.name)
    }
    assert(testLdapUserRepository.counter == 3)
  }

  // Helper to build a mock SearchResult with the given attributes
  private def mockSearchResult(username: String, memberOfDNs: Option[Seq[String]], extraFields: Map[String, String] = Map.empty): SearchResult = {
    val attrs = mock[Attributes]
    val samAttr = mock[Attribute]
    (samAttr.get _).expects().returns(username)
    (attrs.get _).expects("sAMAccountName").returns(samAttr)

    memberOfDNs match {
      case None =>
        (attrs.get _).expects("memberOf").returns(null)
      case Some(dns) =>
        val memberOfAttr = mock[Attribute]
        val namingEnum = mock[NamingEnumeration[Object]]
        val iterator = dns.iterator
        (namingEnum.hasMoreElements _).expects().onCall(() => iterator.hasNext).anyNumberOfTimes()
        (namingEnum.nextElement _).expects().onCall(() => iterator.next().asInstanceOf[Object]).anyNumberOfTimes()
        (memberOfAttr.getAll _).expects().returns(namingEnum)
        (attrs.get _).expects("memberOf").returns(memberOfAttr)
    }

    extraFields.foreach { case (fieldName, _) =>
      (attrs.get _).expects(fieldName).returns(null)
    }

    val searchResult = mock[SearchResult]
    (searchResult.getAttributes _).expects().returns(attrs)
    searchResult
  }

  "resultToUserEntry" should "parse groups correctly when memberOf is present" in {
    val repo = new LdapUserRepository(ldapCfgNoRetries)
    val memberOfDNs = Seq(
      "CN=group1,OU=Groups,DC=domain,DC=com",
      "CN=group2,OU=Groups,DC=domain,DC=com"
    )
    val result = mockSearchResult("testuser", Some(memberOfDNs))

    val user = repo.resultToUserEntry(result)

    user.name shouldBe "testuser"
    user.groups shouldBe Seq("group1", "group2")
    user.optionalAttributes shouldBe Map.empty
  }

  "resultToUserEntry" should "return empty groups when memberOf attribute is absent (service account)" in {
    val repo = new LdapUserRepository(ldapCfgNoRetries)
    val result = mockSearchResult("SVC-eventgate-e2e", None)

    val user = repo.resultToUserEntry(result)

    user.name shouldBe "SVC-eventgate-e2e"
    user.groups shouldBe Seq.empty
    user.optionalAttributes shouldBe Map.empty
  }

  "resultToUserEntry" should "return empty groups when memberOf is empty" in {
    val repo = new LdapUserRepository(ldapCfgNoRetries)
    val result = mockSearchResult("testuser", Some(Seq.empty))

    val user = repo.resultToUserEntry(result)

    user.name shouldBe "testuser"
    user.groups shouldBe Seq.empty
  }

  "resultToUserEntry" should "populate optional attributes when config specifies them" in {
    val ldapCfgWithAttrs = ldapCfgNoRetries.copy(attributes = Some(Map("mail" -> "email")))
    val repo = new LdapUserRepository(ldapCfgWithAttrs)

    val attrs = mock[Attributes]
    val samAttr = mock[Attribute]
    (samAttr.get _).expects().returns("testuser")
    (attrs.get _).expects("sAMAccountName").returns(samAttr)
    (attrs.get _).expects("memberOf").returns(null)

    val mailAttr = mock[Attribute]
    (mailAttr.get _).expects().returns("testuser@example.com")
    (attrs.get _).expects("mail").returns(mailAttr)

    val searchResult = mock[SearchResult]
    (searchResult.getAttributes _).expects().returns(attrs)

    val user = repo.resultToUserEntry(searchResult)

    user.name shouldBe "testuser"
    user.groups shouldBe Seq.empty
    user.optionalAttributes shouldBe Map("email" -> Some("testuser@example.com"))
  }
}
