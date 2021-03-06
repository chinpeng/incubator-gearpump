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
package org.apache.gearpump.streaming.dsl.task

import java.time.Instant

import org.apache.gearpump.Message
import org.apache.gearpump.cluster.UserConfig
import org.apache.gearpump.streaming.Constants._
import org.apache.gearpump.streaming.dsl.plan.functions.FunctionRunner
import org.apache.gearpump.streaming.dsl.task.TransformTask.Transform
import org.apache.gearpump.streaming.task.{Task, TaskContext}

object TransformTask {

  class Transform[IN, OUT](taskContext: TaskContext,
      processor: Option[FunctionRunner[IN, OUT]],
      private var buffer: Vector[Message] = Vector.empty[Message]) {

    def onNext(msg: Message): Unit = {
      buffer +:= msg
    }

    def onWatermarkProgress(watermark: Instant): Unit = {
      var nextBuffer = Vector.empty[Message]
      processor.foreach(_.setup())
      buffer.foreach { message: Message =>
        if (message.timestamp.isBefore(watermark)) {
          processor match {
            case Some(p) =>
              FunctionRunner
                .withEmitFn(p, (out: OUT) => taskContext.output(Message(out, message.timestamp)))
                // .toList forces eager evaluation
                .process(message.value.asInstanceOf[IN]).toList
            case None =>
              taskContext.output(message)
          }
        } else {
          nextBuffer +:= message
        }
      }
      processor.foreach(_.teardown())
      buffer = nextBuffer
    }
  }

}

class TransformTask[IN, OUT](transform: Transform[IN, OUT],
    taskContext: TaskContext, userConf: UserConfig) extends Task(taskContext, userConf) {

  def this(taskContext: TaskContext, userConf: UserConfig) = {
    this(new Transform(taskContext, userConf.getValue[FunctionRunner[IN, OUT]](
      GEARPUMP_STREAMING_OPERATOR)(taskContext.system)), taskContext, userConf)
  }

  override def onNext(msg: Message): Unit = {
    transform.onNext(msg)
  }

  override def onWatermarkProgress(watermark: Instant): Unit = {
    transform.onWatermarkProgress(watermark)
  }
}
