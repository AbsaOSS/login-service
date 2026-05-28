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

package za.co.absa.loginsvc.rest.provider.kerberos

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.BeforeAndAfterEach
import za.co.absa.loginsvc.rest.config.auth.{ActiveDirectoryLDAPConfig, KerberosConfig, LdapUserCredentialsConfig, ServiceAccountConfig}

class KerberosSPNEGOAuthenticationProviderTest extends AnyFlatSpec with Matchers with BeforeAndAfterEach {

  private val serviceAccount = ServiceAccountConfig(
    "CN=%s,OU=Users,DC=example,DC=com",
    Option(LdapUserCredentialsConfig("svc_user", "svc_pass")),
    None,
    None
  )

  private def createLdapConfig(kerberosConfig: KerberosConfig): ActiveDirectoryLDAPConfig = {
    ActiveDirectoryLDAPConfig(
      domain = "example.com",
      url = "ldaps://localhost:636",
      searchFilter = "(sAMAccountName={0})",
      order = 1,
      serviceAccount = serviceAccount,
      enableKerberos = Some(kerberosConfig),
      ldapRetry = None,
      attributes = None
    )
  }

  private def clearKerberosProperties(): Unit = {
    System.clearProperty("javax.net.debug")
    System.clearProperty("sun.security.krb5.debug")
    System.clearProperty("sun.security.krb5.rcache")
    System.clearProperty("java.security.krb5.conf")
  }

  override def beforeEach(): Unit = {
    clearKerberosProperties()
  }

  override def afterEach(): Unit = {
    clearKerberosProperties()
  }

  // initialization tests
  "KerberosSPNEGOAuthenticationProvider" should "set debug system properties to false when debug is None" in {
    val kerberosConfig = KerberosConfig("krb5.conf", "test.keytab", "HTTP/host@REALM", debug = None)
    val ldapConfig = createLdapConfig(kerberosConfig)

    new KerberosSPNEGOAuthenticationProvider(ldapConfig)

    System.getProperty("javax.net.debug") shouldBe "false"
    System.getProperty("sun.security.krb5.debug") shouldBe "false"
  }

  it should "set debug system properties to false when debug is explicitly false" in {
    val kerberosConfig = KerberosConfig("krb5.conf", "test.keytab", "HTTP/host@REALM", debug = Some(false))
    val ldapConfig = createLdapConfig(kerberosConfig)

    new KerberosSPNEGOAuthenticationProvider(ldapConfig)

    System.getProperty("javax.net.debug") shouldBe "false"
    System.getProperty("sun.security.krb5.debug") shouldBe "false"
  }

  it should "set debug system properties to true when debug is explicitly true" in {
    val kerberosConfig = KerberosConfig("krb5.conf", "test.keytab", "HTTP/host@REALM", debug = Some(true))
    val ldapConfig = createLdapConfig(kerberosConfig)

    new KerberosSPNEGOAuthenticationProvider(ldapConfig)

    System.getProperty("javax.net.debug") shouldBe "true"
    System.getProperty("sun.security.krb5.debug") shouldBe "true"
  }

  it should "always set sun.security.krb5.rcache to none" in {
    val kerberosConfig = KerberosConfig("krb5.conf", "test.keytab", "HTTP/host@REALM", debug = Some(true))
    val ldapConfig = createLdapConfig(kerberosConfig)

    new KerberosSPNEGOAuthenticationProvider(ldapConfig)

    System.getProperty("sun.security.krb5.rcache") shouldBe "none"
  }

  it should "set java.security.krb5.conf when krbFileLocation is non-empty" in {
    val kerberosConfig = KerberosConfig("/etc/krb5.conf", "test.keytab", "HTTP/host@REALM", debug = None)
    val ldapConfig = createLdapConfig(kerberosConfig)

    new KerberosSPNEGOAuthenticationProvider(ldapConfig)

    System.getProperty("java.security.krb5.conf") shouldBe "/etc/krb5.conf"
  }

  it should "not set java.security.krb5.conf when krbFileLocation is empty" in {
    val kerberosConfig = KerberosConfig("", "test.keytab", "HTTP/host@REALM", debug = None)
    val ldapConfig = createLdapConfig(kerberosConfig)

    new KerberosSPNEGOAuthenticationProvider(ldapConfig)

    System.getProperty("java.security.krb5.conf") shouldBe null
  }
}

