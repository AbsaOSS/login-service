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

package za.co.absa.loginsvc.rest.config.jwt

import za.co.absa.loginsvc.rest.config.validation.ConfigValidatable

import java.security.KeyPair
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

trait KeyConfig extends ConfigValidatable {
  def algName: String
  def accessExpTime: FiniteDuration
  def refreshExpTime: FiniteDuration
  def keyPair: KeyPair
  def throwErrors(): Unit

  val minAccessExpTime: FiniteDuration = FiniteDuration(10, TimeUnit.MILLISECONDS)
  val minRefreshExpTime: FiniteDuration = minAccessExpTime * 2
}
