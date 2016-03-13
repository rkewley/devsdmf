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

package simutils.random

import akka.actor.Actor

/**
 * Message sent from a [[devsmodel.ModelSimulator]] to a [[SplitStreamActor]] in order to get an [[InitRandom]] message
 * used for initialization of its internal [[SimRandom]] object for random number generation
 */
case class SendInitRandom()

/**
 * Object used to initialize a parallel random number generator.  Upon receipt of this message, a [[devsmodel.ModelSimulator]]
 * will initialize an internal random object with the given seed value, then call skipTo skipSize * numSkips.
 * @param seed  The initial seed
 * @param skipSize  The size of the skips between independent random streams for the generator
 * @param numSkips  The number of skips to execute for the requesting actor
 */
case class InitRandom(seed: Long, skipSize: Long, numSkips: Long)

/**
 * Class to send an [[InitRandom]] message to a [[devsmodel.ModelSimulator]].  Upon receipt of a [[SendInitRandom]] message,
 * this actor will iterate its internal counter by one in order to get the numSkips value.  It will then send a the [[InitRandom]]
 * message.  The model simulator will initialize its internal [[SimRandom]] object with the seed, then it will call
 * skipTo(skipSize * numSkips).  Subsequent calls to next() will yield sequential pseudo-random long values that are independent
 * of values used by other actors.  This stream splitting method is cabable of generating a billion random number streams,
 * each with a billion independent values.  In order to ensure proper use of independent random streams across an entire
 * simulation, all [[devsmodel.ModelSimulator]]s in the simulation, to include those on remote networks, must use the same
 * instance of SplitStreamActor
 * @param skipSize
 */
class SplitStreamActor(val seed: Long, val skipSize: Long) extends Actor {

  var count: Long = 0
  def receive = {
    case SendInitRandom() =>
      sender() ! InitRandom(seed, skipSize, count)
      count = count + 1
  }
}
