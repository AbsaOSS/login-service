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

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import za.co.absa.loginsvc.rest.config.auth.MsEntraConfig

class MsEntraGraphClientTest extends AnyFlatSpec with Matchers {

  private val client = new MsEntraGraphClient(
    MsEntraConfig(
      tenantId = "tenant-id",
      clientId = "client-id",
      clientSecret = None,
      audiences = Nil,
      domains = Some(Map("corp.dsarena.com" -> "CORP")),
      order = 1,
      attributes = None
    )
  )

  "MsEntraGraphClient" should "return a lowercase samAccountName without the domain prefix" in {
    client.resolveMappedUsername("AB006HM", "corp.dsarena.com", "oto.macenauer@absa.africa") shouldBe Some("ab006hm")
  }

  it should "return None when the user's domain is not in the configured allow-list" in {
    client.resolveMappedUsername("AB006HM", "unknown.domain", "oto.macenauer@absa.africa") shouldBe None
  }
}
