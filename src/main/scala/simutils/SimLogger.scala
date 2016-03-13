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

import java.io.{PrintWriter, File}
import java.time.Duration

import akka.actor.{Actor, ActorRef}
import devsmodel.NextTime

/**
  * Message sent to tell the logger to log an external event
  * @param event  The external event to log
  * @param timeOption  An optional time to log.  If None, it will log using the current simulation time
  * @tparam E The type of event
  */
case class LogExternalEvent[E](event: E, timeOption: Option[Duration] = None)

/**
  * Message sent to tell the logger to log an internal event
  * @param event  The external event to log
  * @param timeOption  An optional time to log.  If None, it will log using the current simulation time
  * @tparam E The type of event
  */
case class LogInternalEvent[E](event: E, timeOption: Option[Duration] = None)

/**
  * Message sent to tell the logger to log an output event
  * @param event  The external event to log
  * @param timeOption  An optional time to log.  If None, it will log using the current simulation time
  * @tparam E The type of event
  */
case class LogOutputEvent[E](event: E, timeOption: Option[Duration] = None)

/**
  * Message sent to tell the logger to log the given state
  * @param name  The name of the state variable
  * @param state The state to log
  * @param timeOption  An optional time to log.  If None, it will log using the current simulation time
  * @tparam S  The type of the state variable
  */
case class LogState[S](name: String, state: S, timeOption: Option[Duration] = None)

/**
  * Message sent to tell the logger to log a string to the log file
  * @param s  The string to log
  * @param timeOption  An optional time to log.  If None, it will log using the current simulation time
  */
case class LogToFile(s: String, timeOption: Option[Duration] = None)

/**
  * Message sent to tell the logger to log termination of a simulation
  * @param ret The simulation return code.  0 for success, otherwise indicates
  *            failure.
  * @param s  The string message to log
  * @param timeOption  An optional time to log.  If None, it will log using the current simulation time
  */
case class LogTerminate(returnCode: Int, 
                        s: String, 
                        timeOption: Option[Duration] = None)

/**
  * Message to set design point and iteration for the simulation run
  * @param designPoint  The design point
  * @param iteration  The iteration
  */
case class DesignPointIteration(designPoint: Int, iteration: Int)

/**
  * A class designed to log simulation messages.  The [[devsmodel.RootCoordinator]] sends messages to this logger
  * to keep the current time updated.  Any actor in the simulation can then log messages received and state data to this
  * logger with time parameters.
  * @param fileName  The filename to which log messages are written
  * @param initialTime  The initial simulation time
  */
class SimLogger(val dataLogger:ActorRef, initialTime: Duration, private var designPoint: DesignPointIteration = DesignPointIteration(1,1)) extends Actor {
  var currentTime = initialTime

  def receive = {
    case LogExternalEvent(e, timeOption) =>
      logString( "External event : " + e.toString, timeOption )

    case LogInternalEvent(e, timeOption) =>
      logString( "Internal event : " + e.toString, timeOption )

    case LogOutputEvent( e, timeOption ) =>
      logString( "Output event : " + e.toString, timeOption )

    case LogState( n: String, s, timeOption ) =>
      logString( n + " : " + s.toString, timeOption )

    case LogToFile(s: String, timeOption) =>
      logString( s, timeOption )

    case NextTime(t) =>
      currentTime = t

    case d: DesignPointIteration =>
      designPoint = d

    case LogTerminate( r: Int, s:String, timeOption ) =>
      logString( "Terminate : " + s, timeOption )
      context.system.stop(self)
  }

  protected def logString( s: String, timeOption: Option[Duration] ) = {
    val t = timeOption match {
      case Some(time) => time
      case None => currentTime
    }
    dataLogger ! "" + designPoint.designPoint + ", " + designPoint.iteration + ", " + sender() + ", " + t + ", \"" + s.replaceAll("\"", "") + "\"\n"
  }

}
