/*

Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.

*/

package simutils

import akka.actor.{Actor, UnhandledMessage}
import akka.event.{Logging, LoggingAdapter}

/**
  * A listener for unhandled messages in the actor system.  For a simulation, and unhandled message is a critical error,
  * so the behavior is to log the unhandled message and shut the system down.
  */
class UnhandledMessagesListener extends LoggingActor {

  override def receive = {
    case UnhandledMessage(message, sender, recipient) =>
      log.error("CRITICAL!  No handlers found for message " + message + " from " + sender.path.toString + " to " + recipient.path.toString)
      log.error("Shutting system down")
      context.system.shutdown()
  }
}
