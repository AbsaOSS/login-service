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

package za.co.absa.logingw.utils

import java.util.Optional
import java.util.concurrent.CompletableFuture
import scala.compat.java8.FutureConverters._
import scala.concurrent.Future
import scala.language.implicitConversions

package object implicits {
  implicit class JavaOptExt[C](javaOptional: Optional[C]) {
    def toScalaOption: Option[C] = if (javaOptional.isPresent) Some(javaOptional.get) else None
  }

  implicit def scalaToJavaFuture[T](in: Future[T]): CompletableFuture[T] = in.toJava.toCompletableFuture

}
