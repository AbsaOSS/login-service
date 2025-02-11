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

import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import org.mockito.ArgumentMatchers.{any, anyString, eq => eqMatch}
import org.mockito.Mockito.{mock, when}
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import za.co.absa.loginsvc.rest.config.validation.ConfigValidationException
import za.co.absa.loginsvc.rest.config.validation.ConfigValidationResult.{ConfigValidationError, ConfigValidationSuccess}
import za.co.absa.loginsvc.rest.model.AwsSecret
import za.co.absa.loginsvc.utils.SecretUtil

import java.time.Instant
import java.util.Base64
import scala.concurrent.duration._

class AwsSecretsManagerKeyConfigTest extends AnyFlatSpec with Matchers {

  private val awsSecretsManagerKeyConfig: AwsSecretsManagerKeyConfig = AwsSecretsManagerKeyConfig("Secret",
    "region",
    "private",
    "public",
    "RS256",
    15.minutes,
    9.minutes,
    Option(30.minutes),
    Option(15.minutes),
    Option(5.minutes))

  private val mockSecretsUtil = mock(classOf[SecretUtil])
  private val currentKeyPair = Keys.keyPairFor(SignatureAlgorithm.RS256)
  private val previousKeyPair = Keys.keyPairFor(SignatureAlgorithm.RS256)
  private val currentkeyPairMaps = Map(
    "private" -> Base64.getEncoder.encodeToString(currentKeyPair.getPrivate.getEncoded),
    "public" -> Base64.getEncoder.encodeToString(currentKeyPair.getPublic.getEncoded))
  private val previouskeyPairMaps = Map(
    "private" -> Base64.getEncoder.encodeToString(previousKeyPair.getPrivate.getEncoded),
    "public" -> Base64.getEncoder.encodeToString(previousKeyPair.getPublic.getEncoded))

  private val currentSecret = Some(AwsSecret(currentkeyPairMaps, Instant.now()))
  private val currentSecretAfterLayOver = Some(AwsSecret(currentkeyPairMaps,
    Instant.now().minus(16.minutes.toMillis, java.time.temporal.ChronoUnit.MILLIS)))
  private val currentSecretAfterPhase = Some(AwsSecret(currentkeyPairMaps,
    Instant.now().minus(21.minutes.toMillis, java.time.temporal.ChronoUnit.MILLIS)))
  private val previousSecret = Some(AwsSecret(previouskeyPairMaps,
    Instant.now().minus(6.hours.toMillis, java.time.temporal.ChronoUnit.MILLIS)))

  behavior of "validation"

  "awsSecretsManagerKeyConfig" should "validate expected content" in {
    awsSecretsManagerKeyConfig.validate() shouldBe ConfigValidationSuccess
  }

  it should "fail on invalid algorithm" in {
    awsSecretsManagerKeyConfig.copy(algName = "ABC").validate() shouldBe
      ConfigValidationError(ConfigValidationException("Invalid algName 'ABC' was given."))
  }

  it should "fail on non-negative accessExpTime" in {
    awsSecretsManagerKeyConfig.copy(accessExpTime = 5.milliseconds).validate() shouldBe
      ConfigValidationError(ConfigValidationException(s"accessExpTime must be at least ${KeyConfig.minAccessExpTime}"))
  }

  it should "fail on non-negative refreshExpTime" in {
    awsSecretsManagerKeyConfig.copy(refreshExpTime = 5.milliseconds).validate() shouldBe
      ConfigValidationError(ConfigValidationException(s"refreshExpTime must be at least ${KeyConfig.minRefreshExpTime}"))
  }

  it should "fail on non-negative keyRotationTime" in {
    awsSecretsManagerKeyConfig.copy(pollTime = Option(5.milliseconds)).validate() shouldBe
      ConfigValidationError(ConfigValidationException(s"keyRotationTime must be at least ${KeyConfig.minKeyRotationTime}"))
  }

  it should "fail on non-negative keyPhaseOutTime" in {
    awsSecretsManagerKeyConfig.copy(keyPhaseOutTime = Option(5.milliseconds), keyLayOverTime = None).validate() shouldBe
      ConfigValidationError(ConfigValidationException(s"keyPhaseOutTime must be at least ${KeyConfig.minKeyPhaseOutTime}"))
  }

  it should "fail on keyPhaseOutTime being configured without keyRotationTime" in {
    awsSecretsManagerKeyConfig.copy(pollTime = None, keyLayOverTime = None).validate() shouldBe
      ConfigValidationError(ConfigValidationException(s"keyPhaseOutTime can only be enable if keyRotationTime is enable!"))
  }

  it should "fail on non-negative keyLayOverTime" in {
    awsSecretsManagerKeyConfig.copy(keyLayOverTime = Option(5.milliseconds)).validate() shouldBe
      ConfigValidationError(ConfigValidationException(s"keyLayOverTime must be at least ${KeyConfig.minKeyLayOverTime}"))
  }

  it should "fail on keyLayOverTime being configured without keyRotationTime" in {
    awsSecretsManagerKeyConfig.copy(pollTime = None, keyPhaseOutTime = None).validate() shouldBe
      ConfigValidationError(ConfigValidationException(s"keyLayOverTime can only be enable if keyRotationTime is enable!"))
  }

  it should "fail on missing value" in {
    awsSecretsManagerKeyConfig.copy(secretName = null).validate() shouldBe
      ConfigValidationError(ConfigValidationException("secretName is empty"))
  }

  behavior of "fetchKeySetsFromCloud"

  it should "not use keyLayOver when previousKey is not available" in {
    when (mockSecretsUtil.fetchSecret(anyString(),anyString(),any[Array[String]](),eqMatch(None)))
      .thenReturn(currentSecret)
    when(mockSecretsUtil.fetchSecret(anyString(),anyString(),any[Array[String]](),eqMatch(Some("AWSPREVIOUS"))))
      .thenReturn(None)

    val keysDuringLayover = awsSecretsManagerKeyConfig.fetchKeySetsFromCloud(mockSecretsUtil)

    assert(keysDuringLayover._1.getPrivate == currentKeyPair.getPrivate)
    assert(keysDuringLayover._1.getPublic == currentKeyPair.getPublic)
    assert(keysDuringLayover._2.isEmpty)

    when (mockSecretsUtil.fetchSecret(anyString(),anyString(),any[Array[String]](),eqMatch(None))).thenReturn(currentSecretAfterLayOver)

    val keysAfterLayover = awsSecretsManagerKeyConfig.fetchKeySetsFromCloud(mockSecretsUtil)

    assert(keysAfterLayover._1.getPrivate == currentKeyPair.getPrivate)
    assert(keysAfterLayover._1.getPublic == currentKeyPair.getPublic)
    assert(keysAfterLayover._2.isEmpty)
  }

  it should "not use keyPhaseOut when previousKey is not available" in {
    when (mockSecretsUtil.fetchSecret(anyString(),anyString(),any[Array[String]](),eqMatch(None)))
      .thenReturn(currentSecretAfterPhase)
    when(mockSecretsUtil.fetchSecret(anyString(),anyString(),any[Array[String]](),eqMatch(Some("AWSPREVIOUS"))))
      .thenReturn(None)

    val keysDuringLayover = awsSecretsManagerKeyConfig.fetchKeySetsFromCloud(mockSecretsUtil)

    assert(keysDuringLayover._1.getPrivate == currentKeyPair.getPrivate)
    assert(keysDuringLayover._1.getPublic == currentKeyPair.getPublic)
    assert(keysDuringLayover._2.isEmpty)
  }

  it should "use previousKey during the keyLayOver Period" in {
    when (mockSecretsUtil.fetchSecret(anyString(),anyString(),any[Array[String]](),eqMatch(None)))
      .thenReturn(currentSecret)
    when(mockSecretsUtil.fetchSecret(anyString(),anyString(),any[Array[String]](),eqMatch(Some("AWSPREVIOUS"))))
      .thenReturn(previousSecret)

    val keysDuringLayover = awsSecretsManagerKeyConfig.fetchKeySetsFromCloud(mockSecretsUtil)

    assert(keysDuringLayover._1.getPrivate == previousKeyPair.getPrivate)
    assert(keysDuringLayover._1.getPublic == previousKeyPair.getPublic)
    assert(keysDuringLayover._2.get.getPrivate == currentKeyPair.getPrivate)
    assert(keysDuringLayover._2.get.getPublic == currentKeyPair.getPublic)
  }

  it should "use currentKey after the keyLayOver Period" in {
    when (mockSecretsUtil.fetchSecret(anyString(),anyString(),any[Array[String]](),eqMatch(None)))
      .thenReturn(currentSecretAfterLayOver)
    when(mockSecretsUtil.fetchSecret(anyString(),anyString(),any[Array[String]](),eqMatch(Some("AWSPREVIOUS"))))
      .thenReturn(previousSecret)

    val keysDuringLayover = awsSecretsManagerKeyConfig.fetchKeySetsFromCloud(mockSecretsUtil)

    assert(keysDuringLayover._1.getPrivate == currentKeyPair.getPrivate)
    assert(keysDuringLayover._1.getPublic == currentKeyPair.getPublic)
    assert(keysDuringLayover._2.get.getPrivate == previousKeyPair.getPrivate)
    assert(keysDuringLayover._2.get.getPublic == previousKeyPair.getPublic)
  }

  it should "set the previousKey to None after the keyPhaseOut period" in {
    when (mockSecretsUtil.fetchSecret(anyString(),anyString(),any[Array[String]](),eqMatch(None)))
      .thenReturn(currentSecretAfterPhase)
    when(mockSecretsUtil.fetchSecret(anyString(),anyString(),any[Array[String]](),eqMatch(Some("AWSPREVIOUS"))))
      .thenReturn(previousSecret)

    val keysDuringLayover = awsSecretsManagerKeyConfig.fetchKeySetsFromCloud(mockSecretsUtil)

    assert(keysDuringLayover._1.getPrivate == currentKeyPair.getPrivate)
    assert(keysDuringLayover._1.getPublic == currentKeyPair.getPublic)
    assert(keysDuringLayover._2.isEmpty)
  }
}
