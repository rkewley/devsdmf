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
import simutils._
import simutils.random.{SendInitRandom, InitRandom, SimRandom}
import scala.collection.immutable.TreeMap
import scala.collection.mutable

/**
 * An [[Exception]] indicating that a [[ModelSimulator.DEVSModel]] has received an unhandled event
 * @param m  The text error message
 */
class UnhandledEventException(m: String) extends Exception(m)

/**
 * An [[Exception]] indicating that the time stanps of messages to the [[ModelSimulator]] are not consistent with the time
 *   state of the [[ModelSimulator.DEVSModel]]
 * @param m  The text error message
 */
class SynchronizationException(m: String) extends Exception(m)

/**
 * Message sent telling the [[ModelSimulator]] to generate output by invoking the [[ModelSimulator.DEVSModel.outputFunction]]
 * @param t  The time of the output
 */
case class GenerateOutput(t: Duration)

/**
 * Message sent telling the [[ModelCoordinator]] to empty its event bag and pass messages to handlding models
 * @param t t The time of the message
 */
case class ProcessEventMessages(t: Duration)

/**
 * A message sent upon completion of processing event messages
 * @param t The time of the message
 */
case class ReadyToProcessMessages(t: Duration)

/**
 * An external event message
 * @param event  The external event to be executed by the [[ModelSimulator.DEVSModel]]
 * @param t  The time the message is delivered
 * @param eventIndex An index assigned to the by the [[ModelCoordinator]] to track completion of [[ExternalEvent]] bagging
 * @tparam E  The type of the [[ExternalEvent]]
 */
case class EventMessage[E <: Serializable](event: ExternalEvent[E], t: Duration, eventIndex: Long)

/**
 * Message telling the [[ModelSimulator]] to execute the next [[ModelSimulator.DEVSModel.externalStateTransition()]], [[ModelSimulator.DEVSModel.internalStateTransition()]]
 *   or [[ModelSimulator.DEVSModel.confluentStateTransition()]]
 * @param t  The time of the state transition
 */
case class ExecuteTransition(t: Duration)

/**
 * A message sent by the [[ModelSimulator.DEVSModel]] to its enclosing [[ModelSimulator]] with the output data resulting
  * from calling the [[ModelSimulator.DEVSModel.outputFunction]]
 * It is then forwarded by the [[ModelSimulator]] to its parent
 * @param output  The output data
 * @param t  The time of the output
 * @tparam O  The data type of the output
 */
case class OutputMessage[O](output: O, t: Duration)

/**
 * Message sent by [[ModelSimulator.DEVSModel]] to its enclosing [[ModelSimulator]] and from the  [[ModelSimulator]]
  * to its parent when it is done generating output
 * @param t  The time it is done
 */
case class OutputDone(t: Duration)

/**
 * Message sent by the [[ModelSimulator]] to its parent when it is done adding and [[ExternalEvent]] to the [[ModelSimulator.externalEvents]] list
 * @param t  The time it is done
 * @param eventIndex An index assigned to the [[EventMessage]] by the [[ModelCoordinator]] to track completion of [[ExternalEvent]] bagging
 */
case class BagEventDone(t: Duration, eventIndex: Long)


/**
 * Message sent by the [[ModelSimulator]] to its parent when it is done with a state transition.
 * @param t  The time it is done
 * @param nextTime The next scheduled internal state transition
 */
case class TransitionDone(t: Duration, nextTime: Duration)

/**
 * It is also sent by a [[ModelSimulator.DEVSModel]] to the [[ModelSimulator]] upon completion of an internal state transition
 * @param t  The time it is done
 * @param nextTime The next scheduled internal state transition
 */
case class InternalTransitionDone(t: Duration, nextTime: Duration)

/**
 * It is also sent by a [[ModelSimulator.DEVSModel]] to the [[ModelSimulator]] upon completion of an external state transition
 * @param t  The time it is done
 * @param nextTime The next scheduled internal state transition
 */
case class ExternalTransitionDone(t: Duration, nextTime: Duration)

/**
 * It is also sent by a [[ModelSimulator.DEVSModel]] to the [[ModelSimulator]] upon completion of a confluent state transition
 * @param t  The time it is done
 * @param nextTime The next scheduled internal state transition
 */
case class ConfluentTransitionDone(t: Duration, nextTime: Duration)

/**
 * A message sent to each [[ModelSimulator]] and [[ModelCoordinator]] at simulation termination
 */
case class Terminate()

/**
 * A message sent back up the model hierarchy upon completion of termination activities.  When finally received by the
 * [[RootCoordinator]], it will know that all simulation activities are complete.
 */
case class TerminateDone()

/**
  * Class representing a single state variable in the model's [[]ModelState]].  [[]DynamicStateVariable]]s can change
  * as the DEVSModel executes over time
  * @param timeInState The last time this [[]DynamicStateVariable]] changed its value
  * @param state The value of the state
  * @tparam T The data type of the state
  */
case class DynamicStateVariable[T](timeInState: Duration, state: T)


/**
  * Abstract class representing the static properties of the model that does not change over the execution of the simulation
  */
abstract class ModelProperties

/**
  * Abstract class representing random properties of a model.  A random property is a property that is randomly
  * initialized at run time, but then does not change over the course of simulation time advance.  For example, the
  * skill of an observer to detect targets is a random uniform variable that is set at the beginning of the
  * simulation.  It will be different for eaach simulation run, but it will not change as time advances for the
  * sinulation.  This class is used in the constructor of a [[ModelSimulator.DEVSModel]] to denote a model with
  * no random properties.  A subclass of RandomProperties will contain an instance variable for each random property of
  * the model.
  */
case class RandomProperties()

/**
  * Abstract class representing the dynamic state of the model that changes over execution of the simulation.
  * This class is intended to contain all of the [[ModelSimulator.DEVSModel]]'s [[DynamicStateVariable]]s
  */
abstract class ModelState

/**
  * All internal state of a [[ModelSimulator.DEVSModel]] is accessed and maintained by this object.  It contains
  * a series of [[SimEntityState]] objects that allow access to current and previous values of a state variable.
  * The current values are used during simulation execution.  The previous values are used for logging, as the value of
  * a state variable over time is often an important output of a simulation.  The ModelState keeps track of internal
  * state using two different mechanisms:
  *
  * 1.  The stateVariables list contains a list of single valued state variables
  * that are known prior to the start of a simulation and are tracked over time.  For example, the number of rounds
  * remaining in a weapon is a variable that is known about at simulation start.
  *
  * 2.  Sometimes a DEVSModel must keep track of information it receives during a simulation.  This information
  * is typically received via receipt of [[ExternalEvent]]s.  For example, it must keep track of the location of
  * all external entities.  These locations would sent via an [[ExternalEvent]] messages.  In this case, a map is used
  * keep track of the location of each external object over time.  The internal stateMaps variable lists all
  * of the maps that are used to keep track of state.
  *
  * This class is intended to be automatically code generated from knowledge of the state variables to be tracked.
  * See [[autohold.BeansStateManager]] for an example of an auto-generated subclass of ModelStateManager.
  * @param initialState
  * @tparam S
  */
abstract class ModelStateManager[S <: ModelState](initialState: S) {
  /**
    * Single valued [[SimEntityState]] objects to keep track of internal state for a [[ModelSimulator.DEVSModel]]
    */
  val stateVariables: List[SimEntityState[_]] = List()

  /**
    * Maps to dynamically keep track of state variables that are discovered during simulation execution
    * via [[ExternalEvent]]s.  Each map has a key to represent the name of the value to be tracked
    * and a [[SimEntityState]] for each key in the map.
    */
  val stateMaps: Map[String, mutable.Map[_, SimEntityState[_]]] = Map[String, mutable.Map[_, SimEntityState[_]]]()

  /**
    * Utility function to build the [[SimEntityState]] object for to manage a state variable
    * @param initialState Initial value of the state variable
    * @param name A descriptive text name of the variable - used for logging and data collection.
    * @tparam T The data type of the state variable
    * @return The manager for the variable state.
    */
  def buildSimEntityState[T](initialState: DynamicStateVariable[T], name: String): SimEntityState[T] = {
    val stateTrajectory = new TreeMap[Duration, T] + (initialState.timeInState -> initialState.state)
    new SimEntityState[T](stateTrajectory, name)
  }
}


/**
  * Abstract class representing events that can be executed by the [[ModelSimulator.DEVSModel]].  This class provides a default
  * [[compare]] method that first compares by executionTime and then by a [[hashCode()]].  This is a naive
  * implementation of the notion of a super dense event schedule in which each instant of time can also have a large
  * number of events which themselves are ordered.  This ordering permits a consistent application of pulling the
  * next event from a schedule, even if the two events are scheduled for the same time.
  * @param executionTime  Time the event is executed
  * @param eventData  The data for the event
  * @tparam E  The data type for the eventData
  */
abstract class DEVSEvent[E <: Serializable](val executionTime: Duration, val eventData: E) extends Ordered[DEVSEvent[_ <: Serializable]] {

  override def compare(anotherEvent: DEVSEvent[_ <: Serializable]): Int = {
    val durationCompare =  this.executionTime.compareTo(anotherEvent.executionTime)
    durationCompare match {
      case 0 =>  this.hashCode().compareTo(anotherEvent.hashCode())
      case _ => durationCompare
    }
  }

}

/**
  * Abstract class representing an external event sent to the [[ModelSimulator.DEVSModel]].  An ExternalEvent will result in a
  * call to the [[ModelSimulator.DEVSModel.externalStateTransition()]]
  * @param executionTime  Time the event is executed
  * @param eventData  The data for the event
  * @tparam E  The data type for the eventData
  */
class ExternalEvent[E <: Serializable](override val executionTime: Duration, override val eventData: E) extends DEVSEvent[E](executionTime, eventData)

/**
  * Abstract class representing an internal event within the [[]DEVSModel]].  An InternalEvent will result in a
  * call to the [[ModelSimulator.DEVSModel#internalStateTranstion]].  From the perspective of the DEVS formalism, all the InternalEvents on the event
  * schedule are part of the internal state of the model.
  * @param aTime  Time the event is executed
  * @param anEvent  The data for the event
  * @tparam E  The data type for the eventData
  */
class InternalEvent[E <: Serializable](aTime: Duration, anEvent: E) extends DEVSEvent(aTime, anEvent)

/**
  * Abstract class that is scheduled in order to produce an output of the [[ModelSimulator.DEVSModel]]
  * @param aTime Time of the output
  * @param eventData The data sent as output from the model
  * @tparam E  The data type for the eventData
  */
class OutputEvent[E <: Serializable](aTime: Duration, override val eventData: E) extends DEVSEvent(aTime, eventData)

/**
  * A class that holds the initial events passed into a ModelSimulator at initialization
  * @param internalEvents A list of internal events to be scheduled
  */
case class InitialEvents(internalEvents: List[DEVSEvent[_ <: Serializable]])







/**
  * The ModelSimulator is a faithful representation of the abstract simulator for an atomic [[DEVSModel]] as described by Chow
   * and Ziegler in <a href="http://dl.acm.org/citation.cfm?id=194336">Parallel DEVS: a parallel, hierarchical, modular, modeling formalism</a>
  * A PostScript version of the paper is available <a href="http://www.cs.mcgill.ca/~hv/articles/DiscreteEvent/DEVS/rev-devs.ps.gz">here</a>.
  * Each Simulator class of the simulation will have a subclass of this one, and that subclass can be code generated from
  * knowledge of the state, properties, and events it needs to respond to.  See [[autohold.BeansSimulator]] for an example.
  * Note that each ModelSimulator executes an internal [[DEVSModel]] class that captures properties, state variables,
  * and event transitions.
  * @param properties  A sublcass of [[ModelProperties]] the represents the static properties for the internal [[DEVSModel]]
  * @param initialTime  The initial time of the [[DEVSModel]] being simulated.  This is needed by the constructor in creating the
  *                     abstract value internal [[DEVSModel]]
  * @param initialState The initial state of the internal [[DEVSModel]]
  * @param initialEvents Events to be added to the initial [[Schedule]] of the [[DEVSModel]]
  * @param randomActor  A reference to a [[simutils.random.SplitStreamActor]] to query for random number generation parameters
  * @param simLogger A reference to a [[simutils.SimLogger]] for logging simulation messages
  */
abstract class ModelSimulator[P <: ModelProperties, S <: ModelState, M <: ModelStateManager[S]]
  (val properties: P, initialTime: Duration, initialState: S, initialEvents: InitialEvents, val randomActor: ActorRef, val simLogger: ActorRef) extends LoggingActor with UniqueNames {

  override val supervisorStrategy =
    OneForOneStrategy() {
      case e: ActorInitializationException => {
        SupervisorStrategy.Escalate
        }
      }
  
  /**
   * The DEVS framework model that this actor executes
   */

  protected val devs: DEVSModel[_, _, _, _]

  var random: SimRandom = _

  /**
   * Bag of external event messages to be executed
   */
  private var externalEvents: List[ExternalEvent[_]] = List()

  /**
   * A convenience function to get the current time, or time of last state transitioin, of the [[DEVSModel]]
   * @return The current time of the [[DEVSModel]]
   */
  private def getCurrentTime = devs.currentTime

  /**
   * A convenience functioin to get the time of the next [[DEVSModel.internalStateTransition()]]
   * @return The time of the next [[DEVSModel.internalStateTransition()]]
   */
  private def getNextTime = devs.getNextTime

  /**
   * This function can be overridden by subclasses in order to perform shutdown, logging, and data collection at the
   * end of the simulation.
   */
  def preTerminate() = {
    devs.logState
    devs.modelPreTerminate()
    self ! TerminateDone()
  }


  /**
   * Receive method that handle external messages to execute the [[DEVSModel]].
   * Upon receipt of a [[GetNextTime]] message during simulation initialization, respond with a [[NextTime]] message
   *   that contains the result of [[getNextTime]]
   * Upon receipt of a [[simutils.random.InitRandom]] message, initiaize the internal [[simutils.random.SimRandom]] parallel
   *   random number generator.
   * After receiveing a [[NextTime]] message and a [[simutils.random.InitRandom]] message, transition to the
   *   [[executeSimulation]] state.
   */
  def receive = {

    case GetNextTime() =>
      logDebug(initialTime + "Received GetNextTime.")
      randomActor ! SendInitRandom()

    case InitRandom(seed, skipSize, numSkips) =>
      random = new SimRandom(seed)
      random.skipTo(skipSize * numSkips)
      devs.random = random
      devs.initializeRandomProperties
      logDebug("Sending next time: " + getNextTime + " to parent.")
      context.parent ! NextTime(getNextTime)
      context.become(executeSimulation)
  }

  /**
   * Receive method that handle external messages to execute the [[DEVSModel]].
   * Upon receipt of a [[GetNextTime]] message during simulation initialization, respond with a [[NextTime]] message
   *   that contains the result of [[getNextTime]]
   * Upon receipt of a [[GenerateOutput]] message, call the output function and send output message to parent
   * Upon receipt of an [[EventMessage]], add the message to the [[externalEvents]] list
   * Upon receipt of an [[ExecuteTransition]] message, execute the [[DEVSModel.externalStateTransition()]],
   *   [[DEVSModel.internalStateTransition()]], or [[DEVSModel.confluentStateTransition()]] as required
   */
  def executeSimulation: Receive = {

    case GetNextTime() =>
      logDebug(getCurrentTime + " Received GetNextTime.  Sending " + getNextTime + " to parent.")
      sender() ! NextTime(getNextTime)

    /**
     * Upon receipt of a [[GenerateOutput]] message, call the output function and send output message to parent
     */
    case GenerateOutput(t) =>
      if (t.compareTo(getNextTime) == 0) {
        devs.outputFunction(t)
      }
      else {
        throw new SynchronizationException(t + " in GenerateOutput message does not match next time: " + getNextTime)
      }

    case outputMessage: OutputMessage[_] =>
      logDebug(outputMessage.t + " Received GenerateOutput and generated the following output: " + outputMessage.output)
      context.parent ! outputMessage

    case outputDone: OutputDone =>
      logDebug(sender().path.name + " done with output at " + outputDone.t)
      context.parent ! outputDone

    case EventMessage(externalEvent, t, eventIndex) =>
      logDebug(t + " Received and bagged external event " + externalEvent + " with index " + eventIndex)
      externalEvents = externalEvent :: externalEvents
      simLogger ! LogExternalEvent( externalEvent.eventData, Some(t) )
      sender() ! BagEventDone(t, eventIndex)

    case ProcessEventMessages(t) =>
      context.parent ! ReadyToProcessMessages(t)
      
    case ExecuteTransition(t) =>
      val tNext = devs.timeAdvanceFunction
      logDebug(t + " Received ExecuteTransition")
      logDebug(t + " Current time is " + getCurrentTime + " and externalEvents has " + externalEvents.size + " members.")
      // Determine whether to execute internal, external, or confluent transition function
      if (t.compareTo(tNext) < 0 && t.compareTo(getCurrentTime) >= 0 && externalEvents.nonEmpty) {
        // If the current time is less than simulation next time and there is an external event to execute
        devs.externalStateTransition(t)
      }

      else if (t.compareTo(tNext) == 0 && externalEvents.isEmpty) {
        // If the we are executing at the next time, and there are no external messages, then execute internal state transition
        devs.internalStateTransition(t)
      }
      // If the we are executing at the next time, and there are external messages, then execute confluent state transition
      else if (t.compareTo(tNext) == 0 && externalEvents.nonEmpty) {
        devs.confluentStateTransition(t)
      }

      else {
        throw new SynchronizationException(t + " in ExecuteTransition message time of " + t + " is not between current time "
          + getCurrentTime + " and next time " + devs.timeAdvanceFunction)
      }

    case InternalTransitionDone(t, _) =>
      if(externalEvents.isEmpty) {
        devs.currentTime = t
        context.parent ! TransitionDone(t, getNextTime)
      } else {
        devs.externalStateTransition(t)
      }

    case ExternalTransitionDone(t, _) =>
      devs.currentTime = t
      if (externalEvents.isEmpty)
        context.parent ! TransitionDone(t, getNextTime)
      else
        devs.externalStateTransition(t)

    case ConfluentTransitionDone(t, _) =>
      externalEvents = List()
      devs.currentTime = t
      context.parent ! TransitionDone(t, getNextTime)

    case Terminate() =>
      preTerminate()

    case TerminateDone() =>
      context.parent ! TerminateDone()
  }

  /**
    * The DEVSModel is a faithful representation of the revised or parallel DEVS formalism as described by Chow
    * and Ziegler in <a href="http://dl.acm.org/citation.cfm?id=194336">Parallel DEVS: a parallel, hierarchical, modular, modeling formalism</a>
    * A PostScript version of the paper is available <a href="http://www.cs.mcgill.ca/~hv/articles/DiscreteEvent/DEVS/rev-devs.ps.gz">here</a>.
    *
    * A DEVSModel class will always be an internal class of a [[ModelSimulator]].  This allows a DEVSModel to create its
    * own actors and await completion of computations during calculations.  See [[autohold.BeansSimulator]] with internal
    * class [[autohold.BeansSimulator.BeansModel]], which can be automatically code generated.  See [[autohold.BeansModelImpl]] for
    * an example of a mixed in trait that implements event handlers.
    * @param properties  A sub-class of [[ModelProperties]] holding the static variables that do not change over time
    * @param initialState  A sub-class of [[ModelState]] holding the [[DynamicStateVariable]]s that change during model state transitions
    * @param initialTime  The initial time of the DEVS model
    * @param simLogger A reference to a [[simutils.SimLogger]] for logging simulation messages    * @tparam P  The data type for the [[ModelProperties]]
    * @tparam S  The data type for the  [[ModelState]]
    * @tparam M  The data type of the [[ModelStateManager]]
    */
  abstract class DEVSModel[P <: ModelProperties, R <: RandomProperties, S <: ModelState, M <: ModelStateManager[S]]
  (val properties: P,
   initialState: S,
   initialEvents: InitialEvents,
   initialTime: Duration,
   val simLogger: ActorRef) {

    var randomProperties: R = _

    /**
      * The [[ModelStateManager]] for this simulation
      */
    val state: M = buildStateManager(initialState)

    /**
      * Utility function to enable actor logging by the implementing trait
      * @param s  The string to be written by the logger
      */
    def log_debug(s: String) = logDebug(s)

    /**
      * Utility function called to build the [[ModelStateManager]] for this model
      * @param state The initial state of the model
      * @return Returns the [[ModelStateManager]]
      */
    def buildStateManager(state: S): M

    /**
      * This is the random number generator for this class.  It is initially set to a null value.  When the enclosing
      * [[ModelSimulator]] receives an [[InitRandom]] message, it will initialzie this generator with a
      * seed and skip size then set the value to reference the generator. Once it initializes this generator, it will
      * call the [[initializeRandomProperties]] to initialize [[randomProperties]]
      */
    var random: SimRandom = _

    /**
      * A [[ActorRef]] reference to the enclosing [[ModelSimulator]] so that implementing traits can send messages to it
      */
    val sim = self

    /**
      * This method must be overriden in a subclass to set the value of the [[randomProperties]] object
      */
    def initializeRandomProperties: Unit

    /**
      * A utility method to turn debugging on and off in the [[LoggingActor]]
      * @param d Set true to enable debug logging
      */
    def setDebug(d: Boolean): Unit = {
      debug = d
    }

    /**
      * The current simulation time for the DEVSModel
      */
    var currentTime = initialTime

    /**
      * The internal schedule on which a DEVSModel schedules [[DEVSEvent]]s
      */
    val schedule: Schedule = new Schedule(initialTime)

    /**
      * Adding initial events to the schedule
      */
    initialEvents.internalEvents.foreach { event => schedule.addEvent(event) }

    /**
      * Utility method to allow implementatios of handled events to log a message to the [[simutils.SimLogger]]
      * @param message  The message to be logged
      */
    def logMessage(message: String, timeOption: Option[Duration] = None ) = {
      simLogger ! LogToFile(message, timeOption)
    }

    /**
      * Utility method called upon completion of [[internalStateTransition()]] to have the enclosing [[ModelSimulator]]
      * to send an [[InternalTransitionDone]] to its parent [[ModelCoordinator]]
      * @param t The time of the event transition
      */
    def internalTransitionDone(t: Duration) = {
      logDebug(t + " Completed internal transition.")
      sim ! InternalTransitionDone(t, getNextTime)
    }

    /**
      * Utility method called upon completion of [[externalStateTransition()]] to have the enclosing [[ModelSimulator]]
      * to send an [[ExternalTransitionDone]] to its parent [[ModelCoordinator]]
      * @param t The time of the event transition
      */
    def externalTransitionDone(t: Duration) = {
      logDebug(t + " Completed external transition.")
      sim ! ExternalTransitionDone(t, getNextTime)
    }

    /**
      * A utility method that allows handling event implementations to schedule an output event.
      * @param output  The message to be output
      * @param t The simulation time of the output
      * @tparam T The type of the output message
      */
    def addOutput[T <: Serializable](output: T, t: Duration) = {
      schedule.addEvent(new OutputEvent[T](t, output))
    }

    /**
      * A utility method that allows handling event implementations to schedule an internal event
      * @param eventData The event to schedule
      * @param t The time of the event
      * @tparam T The data type of the event
      */
    def addEvent[T <: Serializable](eventData: T, t: Duration) = {
      schedule.addEvent(new InternalEvent[T](t, eventData))
    }

    /**
     * A convenience method to retrieve the time of next scheduled event.
     * @return  The time of the next scheduled event
     */
    def getNextTime: Duration = schedule.getNextScheduleTime

    /**
      * A method that logs in time order the values of each state variable
      */
    def logState: Unit = {
      state.stateVariables.foreach { stateVariable =>
        stateVariable.getStateTrajectory.stateTrajectory.foreach { case(t, s) =>
          simLogger ! LogState(stateVariable.name, s, Some(t))
        }
      }
    }

    /**
     * This function can be overridden by subclasses in order to perform shutdown, logging, and data collection at the
     * end of the simulation.
     */
    def modelPreTerminate() = {}

    /**
     * This function determines to which state the system will transit after the [[timeAdvanceFunction]] has elapsed
     * It only considers the current state of the system in this transition.  Within a DEVSModel, the current state
     * is a combination of the [[properties]], [[state]], and the [[schedule]].  Upon completion, it must send a
     * [[InternalTransitionDone]] message to the enclosing [[ModelSimulator]]
     * @param t  The time of the internal state transition
     */
    def internalStateTransition(t: Duration) = {
      logDebug(t + " executing internal state transition")
      schedule.getAndRemoveNextSingleEvent match {
        case Some(event) => event match {
          case o: OutputEvent[_] => internalTransitionDone(t)
          case d: DEVSEvent[_] => {
            simLogger ! LogInternalEvent( d.eventData, Some(t) )
            handleInternalStateTransitionData(d.eventData, t)
          }
          case _ => throw new SynchronizationException("Cannot recognize event on schedule: " + event)
        }
        case None => throw new SynchronizationException("Executing internalStateTransition with no event on schedule")
      }
    }

    def handleInternalStateTransitionData[T](d: T, t: Duration)

    /**
     * The external transition function which specifies how the system changes state when an input is received.
     * The effect is to update the [[state]] and to schedule it for an [[internalStateTransition]].
     * The next [[state]] is computed on the basis of the present [[state]], and the events received,  Upon completion, it must send a
     * [[ExternalTransitionDone]] message to the enclosing [[ModelSimulator]]

     * @param t  The time of the external  state transition
     */
    def externalStateTransition(t: Duration): Unit = {
      logDebug(t + " executing external state transition")
      val nextEventOption = externalEvents.headOption
      nextEventOption match {
        case None =>
          throw new SynchronizationException("Executing externalStateTransition with empty external events list")
        case Some(nextEvent) =>
          externalEvents = externalEvents.tail
          handleExternalStateTransitionData(nextEvent.eventData, t)
      }
    }

    def handleExternalStateTransitionData[T](d: T, t: Duration)

    /**
     * The confluent transition function is applied when an [[ExternalEvent]] is received at the same time that an
     * [[internalStateTransition]] is to occur.  The default behavior, encoded here, is to apply the [[internalStateTransition()]]
     * The model will still be imminent because it has an internal event scheduled for the current time, so that
     * will be called next.  This may be overridden to define an explicit confluent transition function.  If overridden, the
     * confluent state transition must send a [[ConfluentTransitionDone]] message to the enclosing [[ModelSimulator]]
     * @param t  The time of the confluent state transition
     */
    def confluentStateTransition(t: Duration): Unit = {
      internalStateTransition(t)
    }


    /**
     * The time advance function uses the internal state of the system to determine the time of the next internal transtion
     * @return  Returns the time of the next internal transition as calculated from the simulation start time
     */
    def timeAdvanceFunction: Duration = schedule.getNextScheduleTime

    /**
     * Output function is called right before the [[internalStateTransition]].  It returns the output of the system
     * which can be used to send messages to other DEVS models in a coupled DEVS system.  Upon completion, send an
     * [[OutputMessage]] to the enclosing [[ModelSimulator]]
     * @param t  The time of the output
     */
    def outputFunction(t: Duration): Unit = {
      schedule.getNextSingleEvent match {
        case Some(e) => e match {
          case o: OutputEvent[_] =>
            sim ! OutputMessage(o.eventData, t)
            simLogger ! LogOutputEvent(o.eventData, Some(t))
          case _ =>
        }
        case None =>
      }
      sim ! OutputDone(t)
    }

  }

}
