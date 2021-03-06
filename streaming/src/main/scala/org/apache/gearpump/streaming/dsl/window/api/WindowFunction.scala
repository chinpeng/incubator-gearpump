/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.gearpump.streaming.dsl.window.api

import java.time.{Duration, Instant}

import org.apache.gearpump.{MIN_TIME_MILLIS, MAX_TIME_MILLIS, TimeStamp}
import org.apache.gearpump.streaming.dsl.window.impl.Window

import scala.collection.mutable.ArrayBuffer

object WindowFunction {

  trait Context[T] {
    def element: T
    def timestamp: Instant
  }
}

trait WindowFunction[T] {

  def apply(context: WindowFunction.Context[T]): Array[Window]

  def isNonMerging: Boolean
}

abstract class NonMergingWindowFunction[T] extends WindowFunction[T] {

  override def isNonMerging: Boolean = true
}

case class GlobalWindowFunction[T]() extends NonMergingWindowFunction[T] {

  override def apply(context: WindowFunction.Context[T]): Array[Window] = {
    Array(Window(Instant.ofEpochMilli(MIN_TIME_MILLIS),
      Instant.ofEpochMilli(MAX_TIME_MILLIS)))
  }

}

case class SlidingWindowFunction[T](size: Duration, step: Duration)
  extends NonMergingWindowFunction[T] {

  def this(size: Duration) = {
    this(size, size)
  }

  override def apply(context: WindowFunction.Context[T]): Array[Window] = {
    val timestamp = context.timestamp
    val sizeMillis = size.toMillis
    val stepMillis = step.toMillis
    val timeMillis = timestamp.toEpochMilli
    val windows = ArrayBuffer.empty[Window]
    var start = lastStartFor(timeMillis, stepMillis)
    windows += Window.ofEpochMilli(start, start + sizeMillis)
    start -= stepMillis
    while (start >= timeMillis) {
      windows += Window.ofEpochMilli(start, start + sizeMillis)
      start -= stepMillis
    }
    windows.toArray
  }

  private def lastStartFor(timestamp: TimeStamp, windowStep: Long): TimeStamp = {
    timestamp - (timestamp + windowStep) % windowStep
  }
}

case class CountWindowFunction[T](size: Int) extends NonMergingWindowFunction[T] {

  override def apply(context: WindowFunction.Context[T]): Array[Window] = {
    Array(Window.ofEpochMilli(0, size))
  }
}

case class SessionWindowFunction[T](gap: Duration) extends WindowFunction[T] {

  override def apply(context: WindowFunction.Context[T]): Array[Window] = {
    Array(Window(context.timestamp, context.timestamp.plus(gap)))
  }

  override def isNonMerging: Boolean = false

}