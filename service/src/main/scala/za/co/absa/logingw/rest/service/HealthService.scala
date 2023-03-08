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

package za.co.absa.logingw.rest.service

import org.springframework.stereotype.{Component, Service}
import org.springframework.boot.actuate.health._
import org.springframework.context.annotation.Bean

@Service
@Component
class HealthService extends HealthIndicator {
  override def health(): Health = {
    if (isApplicationHealthy) {
      Health.up().build()
    } else {
      Health.down().withDetail("Reason", "The application is not healthy").build()
    }
  }

  private def isApplicationHealthy: Boolean = {
    // TO DO: Health Check Logic. This is dependant on the the external resources
    //Required for the Gateway to function correctly, eg: Active Directory if used in Authentication
    true
  }

}
