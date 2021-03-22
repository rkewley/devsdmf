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

import java.io.{File, PrintWriter}
import java.time.Duration

import akka.actor.{Actor, ActorLogging, ActorRef}
import com.typesafe.config.Config
import devsmodel.{EventMessageCase, ExternalEvent, MessageConverter, OutputMessageCase}
import dmfmessages.DMFSimMessages._
import collection.JavaConverters._


abstract class LogEvent[E] extends Serializable {
  def event: E
  def modelName: String
  def timeOption: Option[Duration]
}

/**
  * Message sent to tell the logger to log an external event
  * @param event  The external event to log
  * @param modelName The class name of the DEVS model executing this event
  * @param timeOption  An optional time to log.  If None, it will log using the current simulation time
  * @tparam E The type of event
  */
case class LogExternalEvent[E](event: E, modelName: String, timeOption: Option[Duration] = None) extends LogEvent[E]

/**
  * Message sent to tell the logger to log an internal event
  * @param event  The external event to log
  * @param modelName The class name of the DEVS model executing this event
  * @param timeOption  An optional time to log.  If None, it will log using the current simulation time
  * @tparam E The type of event
  */
case class LogInternalEvent[E](event: E, modelName: String, timeOption: Option[Duration] = None) extends LogEvent[E]

/**
  * Message sent to tell the logger to log an output event
  * @param event  The external event to log
  * @param modelName The class name of the DEVS model executing this event
  * @param timeOption  An optional time to log.  If None, it will log using the current simulation time
  * @tparam E The type of event
  */
case class LogOutputEvent[E](event: E, modelName: String, timeOption: Option[Duration] = None) extends LogEvent[E]

/**
  * Message sent to tell the logger to log the given state
  * @param name  The name of the state variable
  * @param state The state to log
  * @param timeOption  An optional time to log.  If None, it will log using the current simulation time
  * @tparam S  The type of the state variable
  */
case class LogStateCase[S](name: String, modelName: String, state: S, timeOption: Option[Duration] = None) extends Serializable


object SimLogger {
  def buildAny[T <: com.google.protobuf.Message](m: T): com.google.protobuf.Any = com.google.protobuf.Any.pack[T](m)

  def buildLogToFile(logMessage: String, timeString: String): LogToFile = LogToFile.newBuilder()
      .setLogMessage(logMessage).setTimeString(timeString).build

  def buildLogState(variableName: String, t: Duration, state: com.google.protobuf.Message, modelName: String) : LogState = LogState.newBuilder()
    .setVariableName(variableName)
    .setTimeInStateString(t.toString)
    .setState(buildAny(state))
    .setJavaClass(state.getClass.getName)
    .setModelName(modelName)
    .build

  def buildLogDEVSEvent(eventData: DEVSEventData, modelName: String): LogDEVSEvent = {
    LogDEVSEvent.newBuilder()
      .setEvent(eventData)
      .setModelName(modelName)
      .build()
  }

  def buildDesignPointIteration(designPoint: Int, iteration: Int): DesignPointIteration = DesignPointIteration.newBuilder()
    .setDesignPoint(designPoint).setIteration(iteration).build()

  def buildLogTerminate(returnCode: Int, logMessage: String, time: Duration): LogTerminate = LogTerminate.newBuilder()
    .setReturnCode(returnCode).setLogMessage(logMessage).setTimeString(time.toString).build

}

trait FilterLogs {
  def filterLogs(a: Any): Boolean
}

trait SimLogFilter {
  def isLogged(a: Any): Boolean
  def receive: PartialFunction[Any, Unit] = {
    case a: Any =>
      if (isLogged(a) )logMessage(a)
  }
  def logMessage: PartialFunction[Any, Unit]
}

object DontLogStateFilter extends FilterLogs {
  def filterLogs(a: Any): Boolean = a match {
    case _: LogState =>
      false

    case _ =>
      true
  }

}



case class ConfigFilter(config: Config) extends FilterLogs {

  var logMap: Map[String, Boolean] = Map()

  def isMatch(pattern: String, s: String): Boolean = {
    val patterns = pattern.split("\\.")
    val elements = s.split("\\.")
    elements.size == patterns.size && {
      val matches: IndexedSeq[Boolean] = patterns.indices.map { i =>
        patterns(i) == "*" || patterns(i) == elements(i)
      }
      !matches.contains(false)
    }
  }

  def hasMatch(patterns: List[String], s: String): Boolean = {
    patterns.map(p => isMatch(p, s)).contains(true)
  }

  def isVariableLogged(modelName: String, variableName: String): Boolean = {
    val path = "devs.logfilter." + modelName
    config.hasPath(path) && hasMatch(config.getStringList(path).asScala.toList, variableName)
  }

  def filterLogs(a: Any): Boolean = {
    a match {
      case ls: LogState =>
        val id: String = ls.getModelName + "." + ls.getVariableName
        logMap.getOrElse(id, {
          val logThisVariable = isVariableLogged(ls.getModelName, ls.getVariableName)
          logMap = logMap + (id -> logThisVariable)
          logThisVariable
        })
      case LogStateCase(name, modelName, state, timeOption) =>
        val id: String = modelName + "." + name
        logMap.getOrElse(id, {
          val logThisVariable = isVariableLogged(modelName, name)
          logMap = logMap + (id -> logThisVariable)
          logThisVariable
        })

      case _ =>
        true
    }
  }
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
class SimLogger(val dataLogger:ActorRef, initialTime: Duration, filterLogs: FilterLogs, private var designPoint: DesignPointIteration = SimLogger.buildDesignPointIteration(1,1))
  extends Actor with MessageConverter with SimLogFilter {
  var currentTime = initialTime

  override def isLogged(a: Any): Boolean = filterLogs.filterLogs(a)

  def logMessage: PartialFunction[Any, Unit] = {
    case ded: DEVSEventData =>
      val eventData = convertMessage(ded.getEventData, ded.getJavaClass)
      logString( ded.getEventType + " event: " + eventData, ded.getExecutionTimeString )

    case lde: LogDEVSEvent =>
      val eventData = convertMessage(lde.getEvent.getEventData, lde.getEvent.getJavaClass)
      logString( lde.getModelName + " " + lde.getEvent.getEventType + " event: " + eventData, lde.getEvent.getExecutionTimeString )

    case ev: ExternalEvent[_] =>
      logString("EXTERNAL event: " + ev.eventData, ev.executionTime.toString)

    case LogExternalEvent(e, modelName, timeOption) =>
      logString( "External event : " + e.toString, timeOption )

    case LogInternalEvent(e, modelName, timeOption) =>
      logString( "Internal event : " + e.toString, timeOption )

    case LogOutputEvent( e, modelName, timeOption ) =>
      logString( "Output event : " + e.toString, timeOption )

    case ls: LogState =>
      val state = convertMessage(ls.getState, ls.getJavaClass)
      logString( ls.getVariableName + " : " + state, ls.getTimeInStateString )

    case LogStateCase(name, modelName, state, timeOption) =>
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

