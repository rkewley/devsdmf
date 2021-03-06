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

package devsmodel

import java.time.Duration

import akka.actor._
import akka.actor.SupervisorStrategy._
import akka.event.{Logging, LoggingAdapter}
import akka.serialization.Serialization
import dmfmessages.DMFSimMessages.GenerateOutput
import simutils._
import simutils.random.{InitRandom, SendInitRandom, SplitStreamActor}

import scala.concurrent.duration._
import dmfmessages.DMFSimMessages._

/**
  * This class is the parent class of all simulations.  Its main functions are to manage time advance for the simulation,
  * create the [[simutils.random.SplitStreamActor]] to enable parallel random numbers, and commuicate with the [[SimLogger]] so that
  * it can keep track of the simulation cloack for logging.
 *
  * @param initialTime The initial time of the simulation
  * @param stopTime The stop time of the simulation
  * @param randomSeed Initial random seed passed to the [[SplitStreamActor]]
  * @param splitRandomStreamSize The number of values in the random strem given to each [[ModelSimulator.DEVSModel]] by
  *                              the [[SplitStreamActor]]
  * @param designPointIteration The design point and iteration for this simulation run
  * @param simLogger The simulation logger
  */
abstract class RootCoordinator(val initialTime: Duration,
                               val stopTime: Duration,
                               randomSeed: Long,
                               splitRandomStreamSize: Long,
                               val designPointIteration: DesignPointIteration,
                               val simLogger: ActorRef) extends Actor with ActorLogging with UniqueNames {

  override val supervisorStrategy =
    OneForOneStrategy() {
      case e: Exception =>
        simLogger ! SimLogger.buildLogToFile(e.toString, currentTime.toString)
        self ! PoisonPill
        SupervisorStrategy.Stop
    }

  /**
    * Manages the parallel random streams for the simulation
    */
  protected val randomActor = context.actorOf(Props(new SplitStreamActor(randomSeed, splitRandomStreamSize)), name = "SplitStreamActor")

  /**
   * This abstract value must be created by sub-classes of this RootCoordinator.  It will be a [[ModelCoordinator]]
   */
  val topCoordinator: ActorRef

  /**
   * The next internal state transition of the [[topCoordinator]]
   */
  var nextTime: Duration = Duration.ofSeconds(0)

  /**
   * The current time of the simulation
   */
  var currentTime: Duration = Duration.ofSeconds(0)

  /**
   * The success indicator for the simulation
   */
  var success: Int = -1

  /**
   * This function can be overridden by subclasses in order to perform shutdown, logging, and data collection at the
   * end of the simulation.  It is called as the first step of the [[terminate]] function
   */
  def preTerminate() = {}

  /**
   * Stop the simulation by first calling [[preTerminate]] then shutting down the [[akka.actor.ActorSystem]]
   */
  def terminate() = {
    preTerminate()
    log.debug("System terminating.")
    //context.parent ! Terminate()
    success = 0
    simLogger ! SimLogger.buildLogTerminate(success, "Simulation Complete", currentTime)
    context.stop(self)
  }

  /**
    *  Actor receive method calls receiveMessages and forwards to receiveCustomMessages if unhandled
    */
  def receive = {
    receiveMessages orElse receiveCustomMessages
  }

  /**
   * Upon receipt of a [[StartSimulation]] message, initialize the simulation by sending a [[GetNextTime]] to the
   * [[topCoordinator]].
   *
   * Upon receipt of a [[NextTime]] message, send a [[dmfmessages.DMFSimMessages.GenerateOutput]] message to the [[topCoordinator]] in order to
   * begin simulation execution.  Then transition to the [[awaitingOutputDone]] state.
   */
  def receiveMessages: Receive = {

    case s: StartSimulation =>
      simLogger ! designPointIteration
      topCoordinator ! GetNextTime.newBuilder()
          .setSerializedRandomActor(Serialization.serializedActorPath(randomActor))
          .setSerializedSimLogger(Serialization.serializedActorPath(simLogger))
          .build()
      log.debug("Starting simulatioin")

    case nt: NextTime =>
      topCoordinator ! GenerateOutput.newBuilder().setTimeString(nt.getTimeString).build()
      simLogger ! nt
      context.become(awaitingOutputDone)
      log.debug("Received next time of " + nt.getTimeString + " from " + sender().path.name)

  }

  /**
    *  This method can be overridden to receive custom user messages by the RootCoordinator actor
    */
  def receiveCustomMessages: Receive = {

    case s =>
      log.warning(s"Received unknown message ${s.getClass.getName}")

  }

  /**
   *  In this state, the RootCoordinator is waiting for the [[topCoordinator]] to finish generating output.
   *  and to finish passing messages generated by that output.  Upon receipt of both a [[OutputDone]] message
   *  send a [[dmfmessages.DMFSimMessages.ProcessEventMessages]] message to the [[topCoordinator]].  Upon receipt of
   *  an [[OutputDone]] message, send an [[ExecuteTransition]] message to the [[topCoordinator]].  Then transition to
   *  the [[awaitingTransitionDone]] state.
   */
  def awaitingOutputDone: Receive = {
    case od: OutputDone =>
      val t: Duration = Duration.parse(od.getTimeString)
      log.debug(t + " Received output done from " + sender().path.name)
      topCoordinator ! ModelSimulator.buildProcessEventMessages(t)

    case rpm: ReadyToProcessMessages =>
      val t: Duration = Duration.parse(rpm.getTimeString)
      topCoordinator ! ModelSimulator.buildExecuteTransition(t)
      context.become(awaitingTransitionDone)
      log.debug(t + " Received ReadyToProcessMessages from " + sender().path.name + ".  Executing state tranistions.")
  }

  /**
   * In this state, the RootCoordinator is waiting for the [[topCoordinator]] to finish state transitions.  Upo receipt
   * of a [[TransitionDone]] message, first check to see of the [[TransitionDone]] nextTime is less than [[TimeManager.infiniteTime]].
   * If so, send a [[GenerateOutput]] message to the [[topCoordinator]] and transition to the [[awaitingOutputDone]] state.
   * Otherwise, [[terminate]] the simulation.
   */
  def awaitingTransitionDone: Receive = {
    case td: StateTransitionDone =>
      val t: Duration = Duration.parse(td.getTimeString)
      val nextTransition: Duration = Duration.parse(td.getNextTimeString)
      log.debug(t + " " + sender().path.name + " completed state transition.")
      if (nextTransition.compareTo(TimeManager.infiniteTime) < 0 && nextTransition.compareTo(stopTime) <= 0) {
        simLogger ! ModelSimulator.buildNextTime(nextTransition)
        topCoordinator ! GenerateOutput.newBuilder().setTimeString(nextTransition.toString).build()//GenerateOutput(nextTransition)
        log.debug(t + " Generating output")
        context.become(awaitingOutputDone)
      }
      else {
        topCoordinator ! Terminate.newBuilder().build()
        log.debug( t + " Termination criteria have been met.")
      }

    case td: TerminateDone =>
      terminate()
  }

  override def postStop() = {
    if( -1 == success ) {
      simLogger ! SimLogger.buildLogTerminate(success, "Error: Unexptected exit", currentTime)
    }
  }
}
