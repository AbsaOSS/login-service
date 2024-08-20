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

import org.mockito.Mockito.mock
import org.springframework.security.authentication.{AnonymousAuthenticationToken, UsernamePasswordAuthenticationToken}
import org.springframework.security.core.authority.AuthorityUtils
import org.springframework.security.core.{Authentication, GrantedAuthority}
import org.springframework.security.kerberos.authentication.{KerberosServiceRequestToken, KerberosTicketValidation}
import za.co.absa.loginsvc.model.User
import za.co.absa.loginsvc.rest.model.KerberosUserDetails

import java.util


object FakeAuthentication {

  val fakeUser: User = User("fakeUser",
    Seq("first-fake-group", "second-fake-group", "third-fake-group", "another-group"),
    Map("mail" -> Some("fake@gmail.com"), "displayname" -> Some("Fake Name")))

  val fakeUserAuthentication: Authentication = new UsernamePasswordAuthenticationToken(
    fakeUser, "fakePassword", new util.ArrayList[GrantedAuthority]()
  )

  val fakeAnonymousAuthentication: Authentication = new AnonymousAuthenticationToken(
    "key", "anonymous", AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")
  )

  val fakeUserDetails: KerberosUserDetails = KerberosUserDetails(fakeUser)

  private val ticketValidation = mock(classOf[KerberosTicketValidation])
  private val ticket = Array[Byte](1,2,3,4)

  val fakeUserDetailsAuthentication: Authentication = new KerberosServiceRequestToken(
    fakeUserDetails, ticketValidation, fakeUserDetails.getAuthorities, ticket
  )

}
