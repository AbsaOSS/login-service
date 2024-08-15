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

package za.co.absa.loginsvc.rest.config.auth

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import za.co.absa.loginsvc.rest.config.validation.ConfigValidationException
import za.co.absa.loginsvc.rest.config.validation.ConfigValidationResult.{ConfigValidationError, ConfigValidationSuccess}

class KerberosConfigTest extends AnyFlatSpec with Matchers{

  private val kerberosConfig = new KerberosConfig("krb5", "keytab", "spn", None)

  "kerberosConfig" should "return the correct validation results" in {
    kerberosConfig.validate() shouldBe ConfigValidationSuccess

    kerberosConfig.copy(debug = Some(true)).validate() shouldBe ConfigValidationSuccess

    kerberosConfig.copy(krbFileLocation = null).validate() shouldBe
      ConfigValidationError(ConfigValidationException("krbFileLocation is empty"))

    kerberosConfig.copy(keytabFileLocation = null).validate() shouldBe
      ConfigValidationError(ConfigValidationException("keytabFileLocation is empty"))

    kerberosConfig.copy(spn = null).validate() shouldBe
      ConfigValidationError(ConfigValidationException("spn is empty"))
  }
}
