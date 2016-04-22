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
import simutils._
import simutils.random.{SendInitRandom, InitRandom, SplitStreamActor}
import scala.concurrent.duration._

/**
 * The message is sent at simulation initialization from the [[RootCoordinator]] to the [[RootCoordinator.topCoordinator]]
 * in order to determine the first transition time in the simulation
 */
case class GetNextTime()

/**
 * Upon receiveing a [[GetNextTime]] message from the [[RootCoordinator]], the [[RootCoordinator.topCoordinator]] replies
 * with this message
 * @param t  The time of the first state transition in the [[RootCoordinator.topCoordinator]]
 */
case class NextTime(t: Duration)

/**
 * This message when sent to the [[RootCoordinator]] starts the simulation
 */
case class StartSimulation()

/**
  * This class is the parent class of all simulations.  Its main functions are to manage time advance for the simulation,
  * create the [[simutils.random.SplitStreamActor]] to enable parallel random numbers, and commuicate with the [[SimLogger]] so that
  * it can keep track of the simulation cloack for logging.
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
                               val simLogger: ActorRef) extends LoggingActor with UniqueNames {

  override val supervisorStrategy =
    OneForOneStrategy() {
      case e: Exception => 
        simLogger ! LogToFile( e.toString )
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
    logDebug("System terminating.")
    //context.parent ! Terminate()
    success = 0
    simLogger ! LogTerminate( success, "Simulation Complete" )
    context.stop(self)
  }

  /**
   * Upon receipt of a [[StartSimulation]] message, initialize the simulation by sending a [[GetNextTime]] to the
   * [[topCoordinator]].
   *
   * Upon receipt of a [[NextTime]] message, send a [[GenerateOutput]] message to the [[topCoordinator]] in order to
   * begin simulation execution.  Then transition to the [[awaitingOutputDone]] state.
   */
  def receive = {

    case StartSimulation() =>
      simLogger ! designPointIteration
      topCoordinator ! GetNextTime()
      logDebug("Starting simulatioin")

    case NextTime(t) =>
      topCoordinator ! GenerateOutput(t)
      simLogger ! NextTime(t)
      context.become(awaitingOutputDone)
      logDebug("Received next time of " + t + " from " + sender().path.name)

  }

  /**
   *  In this state, the RootCoordinator is waiting for the [[topCoordinator]] to finish generating output.
   *  and to finish passing messages generated by that output.  Upon receipt of both a [[OutputDone]] message
   *  send a [[ProcessEventMessages]] message to the [[topCoordinator]].  Upon receipt of
   *  an [[OutputDone]] message, send an [[ExecuteTransition]] message to the [[topCoordinator]].  Then transition to
   *  the [[awaitingTransitionDone]] state.
   */
  def awaitingOutputDone: Receive = {
    case OutputDone(t) =>
      logDebug(t + " Received output done from " + sender().path.name)
      topCoordinator ! ProcessEventMessages(t)

    case ReadyToProcessMessages(t) =>
      topCoordinator ! ExecuteTransition(t)
      context.become(awaitingTransitionDone)
      logDebug(t + " Received ReadyToProcessMessages from " + sender().path.name + ".  Executing state tranistions.")
  }

  /**
   * In this state, the RootCoordinator is waiting for the [[topCoordinator]] to finish state transitions.  Upo receipt
   * of a [[TransitionDone]] message, first check to see of the [[TransitionDone.nextTime]] is less than [[TimeManager.infiniteTime]].
   * If so, send a [[GenerateOutput]] message to the [[topCoordinator]] and transition to the [[awaitingOutputDone]] state.
   * Otherwise, [[terminate]] the simulation.
   */
  def awaitingTransitionDone: Receive = {
    case TransitionDone(t, nextTransition) =>
      logDebug(t + " " + sender().path.name + " completed state transition.")
      if (nextTransition.compareTo(TimeManager.infiniteTime) < 0 && nextTransition.compareTo(stopTime) <= 0) {
        simLogger ! NextTime(nextTransition)
        topCoordinator ! GenerateOutput(nextTransition)
        logDebug(t + " Generating output")
        context.become(awaitingOutputDone)
      }
      else {
        topCoordinator ! Terminate()
        logDebug( t + " Termination criteria have been met.")
      }

    case TerminateDone() =>
      terminate()
  }

  override def postStop() = {
    if( -1 == success ) {
      simLogger ! LogTerminate( success, "ERROR: Unexpected exit" )
    }
  }
}
