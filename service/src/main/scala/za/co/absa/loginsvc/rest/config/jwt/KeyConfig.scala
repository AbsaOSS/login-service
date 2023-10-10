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
