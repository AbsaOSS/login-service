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

package za.co.absa.loginsvc.utils

object OptionUtils {

  /**
   * For `target`, either return as is (if `optValueToApply` is None) or apply fn `func`
   *
   * @param target
   * @param optValueToApply
   * @param func
   * @tparam A
   * @tparam B
   * @return
   */
  def applyIfDefined[A, B](target: A, optValueToApply: Option[B], func: (A, B) => A): A = {
    optValueToApply.map { valueToApply =>
      func(target, valueToApply)
    }.getOrElse {
      target
    }
  }

  implicit class ImplicitBuilderExt[TargetType, OptType](val builder: TargetType) extends AnyVal {

    /**
     * Useful to conditionally apply value from Option if defined
     * @param opt option that is the deciding factor
     * @param fn function to apply to caller if opt is Some(value) -> value will be used in fn
     * @tparam OptType
     * @return caller with the fn conditionally applied to
     */
    def applyIfDefined[OptType](opt: Option[OptType])(fn: (TargetType, OptType) => TargetType): TargetType = {
      OptionUtils.applyIfDefined(builder, opt, fn)
    }
  }

}
