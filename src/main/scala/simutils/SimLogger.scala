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
import com.google.protobuf.{Any, GeneratedMessage}
import devsmodel.{OutputMessageCase, ExternalEvent, EventMessageCase, MessageConverter}
import dmfmessages.DMFSimMessages._


/**
  * Message sent to tell the logger to log an external event
  * @param event  The external event to log
  * @param timeOption  An optional time to log.  If None, it will log using the current simulation time
  * @tparam E The type of event
  */
case class LogExternalEvent[E](event: E, timeOption: Option[Duration] = None) extends Serializable

/**
  * Message sent to tell the logger to log an internal event
  * @param event  The external event to log
  * @param timeOption  An optional time to log.  If None, it will log using the current simulation time
  * @tparam E The type of event
  */
case class LogInternalEvent[E](event: E, timeOption: Option[Duration] = None) extends Serializable

/**
  * Message sent to tell the logger to log an output event
  * @param event  The external event to log
  * @param timeOption  An optional time to log.  If None, it will log using the current simulation time
  * @tparam E The type of event
  */
case class LogOutputEvent[E](event: E, timeOption: Option[Duration] = None) extends Serializable

/**
  * Message sent to tell the logger to log the given state
  * @param name  The name of the state variable
  * @param state The state to log
  * @param timeOption  An optional time to log.  If None, it will log using the current simulation time
  * @tparam S  The type of the state variable
  */
case class LogStateCase[S](name: String, state: S, timeOption: Option[Duration] = None) extends Serializable



object SimLogger {
  def buildAny[T <: GeneratedMessage](m: T): Any = Any.pack[T](m)

  def buildLogToFile(logMessage: String, timeString: String): LogToFile = LogToFile.newBuilder()
      .setLogMessage(logMessage).setTimeString(timeString).build

  def buildLogState(variableName: String, t: Duration, state: GeneratedMessage) : LogState = LogState.newBuilder()
      .setVariableName(variableName).setTimeInStateString(t.toString).setState(buildAny(state)).build

  def buildDesignPointIteration(designPoint: Int, iteration: Int): DesignPointIteration = DesignPointIteration.newBuilder()
    .setDesignPoint(designPoint).setIteration(iteration).build()

  def buildLogTerminate(returnCode: Int, logMessage: String, time: Duration): LogTerminate = LogTerminate.newBuilder()
    .setReturnCode(returnCode).setLogMessage(logMessage).setTimeString(time.toString).build
}


/**
  * A class designed to log simulation messages.  The [[devsmodel.RootCoordinator]] sends messages to this logger
  * to keep the current time updated.  Any actor in the simulation can then log messages received and state data to this
  * logger with time parameters.
 *
  * @param dataLogger  A data logger actor to send log messages to
  * @param initialTime  The initial simulation time
  * @param designPoint The design point and iteration for a model run set
  */
abstract class SimLogger(val dataLogger:ActorRef, initialTime: Duration, private var designPoint: DesignPointIteration = SimLogger.buildDesignPointIteration(1,1)) extends Actor with MessageConverter {
  var currentTime = initialTime

  def receive = {
    case ded: DEVSEventData =>
      val eventData = convertEvent(ded.getEventData)
      logString( ded.getEventType + " event: " + eventData, ded.getExecutionTimeString )

    case ev: ExternalEvent[_] =>
      logString("EXTERNAL event: " + ev.eventData, ev.executionTime.toString)

    case om: OutputMessageCase[_] =>
      logString("OUTPUT event: " + om.output, om.t.toString)

    case LogExternalEvent(e, timeOption) =>
      logString( "External event : " + e.toString, timeOption )

    case LogInternalEvent(e, timeOption) =>
      logString( "Internal event : " + e.toString, timeOption )

    case LogOutputEvent( e, timeOption ) =>
      logString( "Output event : " + e.toString, timeOption )

    case ls: LogState =>
      val state = convertState(ls.getState)
      logString( ls.getVariableName + " : " + state, ls.getTimeInStateString )

    case LogStateCase(name, state, timeOption) =>
      logString(name + ": " + state, timeOption.getOrElse(currentTime).toString)

    case lf: LogToFile =>
      logString( lf.getLogMessage, lf.getTimeString )

    case nt: NextTime =>
      currentTime = Duration.parse(nt.getTimeString)

    case d: DesignPointIteration =>
      designPoint = d

    case lt: LogTerminate => //LogTerminate( r: Int, s:String, timeOption ) =>
      logString( "Terminate : " + lt.getLogMessage, lt.getTimeString )
      context.system.stop(self)
  }

  protected def logString(s: String, timeString: String): Unit = {
    val t: Option[Duration] = timeString match {
      case "" => None
      case _ => Some(Duration.parse(timeString))
    }
    logString(s, t)
  }

  protected def logString( s: String, timeOption: Option[Duration] ): Unit = {
    val t = timeOption match {
      case Some(time) => time
      case None => currentTime
    }
    dataLogger ! "" + designPoint.getDesignPoint + ", " + designPoint.getIteration + ", " + sender() + ", " + t + ", \"" + s.replaceAll("\"", "") + "\"\n"
  }

}
