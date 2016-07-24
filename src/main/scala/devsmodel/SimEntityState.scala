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

import akka.actor.ActorRef

import scala.collection.immutable.TreeMap
import java.time.Duration

class SimStateException(msg: String) extends Exception(msg)

/**
  * A trait to implement getting state variables from a [[SimEntityState]]
  */
trait GetState {

  /**
    * Gets the state of a variable at the reference time.  Throws an error if there is no value for a variable
    * at the requested time.
    * @param referenceTime Time for the retrieved state value
    * @param states The states map for the variable
    * @tparam T The type fo the retrieved variable
    * @return The value of the variable and time for that value
    */
  protected def getState[T <: Serializable](referenceTime: Duration, states: TreeMap[Duration, T]): DynamicStateVariable[T] = {
    val stateBefore = states.filter({case (time, state) => time.compareTo(referenceTime) <= 0})
    stateBefore.lastOption match {
      case Some((time, state)) => DynamicStateVariable[T](time, state)
      case None => throw new SynchronizationException("No state for time " + referenceTime + " May not have been initialized")
    }
  }


  /**
    * Gets the state of a variable at the reference time returning an [[Option]]
    * @param referenceTime Time for the retrieved state value
    * @param states The states map for the variable
    * @tparam T The type fo the retrieved variable
    * @return The value of the variable and time for that value as an [[Option]]
    */
  protected def getStateOption[T <: Serializable](referenceTime: Duration, states: TreeMap[Duration, T]): Option[DynamicStateVariable[T]] = {
    val stateBefore = states.filter({case (time, state) => time.compareTo(referenceTime) <= 0})
    stateBefore.lastOption match {
      case Some((time, state)) => Some(DynamicStateVariable[T](time, state))
      case None => None
    }
  }

}

/**
  * A utility class to hold an immutable version of the trajectory of a state variable.  This is used when these values
  * are passed externally so that external objects cannot change the state ov a variable
  * @param stateTrajectory The ordered trajectory of the state variable.
  * @tparam T The type of the state variable
  */
case class ImmutableSimEntityState[T <: Serializable](val stateTrajectory: TreeMap[Duration, T]) extends GetState {
  def getState(referenceTime: Duration): DynamicStateVariable[T] = getState(referenceTime, stateTrajectory)
  def getStateOption(referenceTime: Duration): Option[DynamicStateVariable[T]] = getStateOption(referenceTime, stateTrajectory)

}

/**
  * State variables in a [[ModelSimulator.DEVSModel]] are immutable classes whose values change over time during simulation
  * execution.  In a simulation, it is important to know the value of a state variable and also the amount of time
  * that has passed since that variable changed its value.  This object tracks variable values over the duration of a
  * simulation.
  * @param stateTrajectory This data structure keeps track of the values of a state variable over time.  Th initial
  *                        value passed in here is usually an empty [[TreeMap]]
  * @param name The text name of the state variable
  * @param recordingState If false, once a new value of state is set, previous values will be dropped.  The default
  *                       value is true.  Setting to false will conserve memory, but limit traceabilility
  * @tparam T The type of the state variable
  */
class SimEntityState[T <: Serializable](private var stateTrajectory: TreeMap[Duration, T], val name: String, var recordingState: Boolean = true) extends GetState {

  /**
    * Method sets a new value for the state variable at a specific time
    * @param newState New value for the state variable
    * @param time The simulation time that the value is set
    */
	def setState(newState: T, time: Duration) {
      stateTrajectory += (time -> newState)
      if (!recordingState) dropStateBefore(time)
  }

  /**
    * Retrieves the latest [[DynamicStateVariable]] value for a state variable
    * @return The latest value
    */
  def getLatestDynamicStateValue: DynamicStateVariable[T] = {
    stateTrajectory.lastOption match {
      case Some((time, s)) => DynamicStateVariable[T](time, s)
      case None => throw new SimStateException("No initial state")
    }
  }

  /**
    * Retrieves the time of the latest state update for a state variable
    * @return The time of the latest update
    */
  def getLatestUpdateTime: Duration = getLatestDynamicStateValue.timeInState

  /**
    * Gets only the state variable value for the latest state of a variable
    * @return The state value
    */
  def getLatestState: T = getLatestDynamicStateValue.state

  /**
    * Gets the entire state trajectory of a variable over the duration of the simulation
    * @return The immutable state trajectory of a variable
    */
  def getStateTrajectory = ImmutableSimEntityState(stateTrajectory)

  /**
    *  Removes all state updates that took place after the timeInPast
    *  Returns true if any of the state values had to be dropped, false if there were no changes
    *  This functionality is a remnant of the version of DEVS-DMF that implemented time warp parallel
    *  execution.  This functionality is not used in the current PDEVS (Parallel DEVS) implementation,
    *  but it is left in to support a potential return to time warp execution.
    * @param timeInPast The time after which all values are removed
    * @return This flag indicates whether any variable changes took place after timeInPast
    */
  def rollbackTo(timeInPast: Duration): Boolean = {
    val oldCount = stateTrajectory.size
    val newStateTrajectory = stateTrajectory.filter({case (time, state) => time.compareTo(timeInPast) < 0})
    stateTrajectory = newStateTrajectory.isEmpty match {
      case true =>
        stateTrajectory.isEmpty match { // if the new state trajectory is empty, put back the first item that would have been there at simulation initialization
          case true => stateTrajectory  // leave it empty if it already was
          case false => TreeMap(stateTrajectory.head)  // otherwise, put the first item back
        }
      case false => newStateTrajectory  // otherwise, use the filtered list
    }
    oldCount != stateTrajectory.size
  }

  /**
    * Returns a trajectory of all state variable values before a certain time
    * @param timeInPast  The time in the past before which to retrieve all values
    * @return The state trajectory of this state variable up to an including the value at timeInPast
    */
  def getStateBefore(timeInPast: Duration) = {
    val currentState = getState(timeInPast)
    val filteredStateTrajectory = stateTrajectory.filter({case (time, state) => time.compareTo(timeInPast) <= 0})
    val newStateTrajectory = filteredStateTrajectory.size match {
      case x if x == 1 => filteredStateTrajectory
      case x if x > 1 => filteredStateTrajectory - currentState.timeInState
      case _ => throw new SimStateException("No state value at " + timeInPast + ". May not have been initialized.")
    }
    ImmutableSimEntityState[T](newStateTrajectory)
  }

  /**
    * Drops all state values before a certain time in the past.  Currently, a DEVS-DMF simulation stores all values of
    * a state variable until simulation termination, when these values are finally logged.  In a simulation that had a
    * large number of values that frequently changed, this could lead to an explostion of memory required.  This
    * functionality allows the simulation to call [[getStateBefore()]] to retrieve past values to log, the call this
    * function to drop those values from memory.  If logging the values over time is not needed, the values
    * can simply be dropped by calling this function.
    * @param timeInPast The time before wich to drop all values
    */
  def dropStateBefore(timeInPast: Duration) = {
    val currentState = getState(timeInPast)
    stateTrajectory = stateTrajectory.filter({case (time, state) => time.compareTo(timeInPast) > 0})
    stateTrajectory += currentState.timeInState -> currentState.state
  }

  /**
    * Gets the state of a variable at a specific time
    * @param referenceTime The simulation time for which the value is requested
    * @return The value of the state variable at referenceTime
    */
  def getState(referenceTime: Duration): DynamicStateVariable[T] = getState(referenceTime, stateTrajectory)

}
