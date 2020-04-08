package simutils

import java.time.Duration

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import com.google.gson.{Gson, GsonBuilder}
import com.google.protobuf.Message
import com.google.protobuf.util.JsonFormat
import devsmodel.{ExternalEvent, MessageConverter}
import dmfmessages.DMFSimMessages.{DEVSEventData, DesignPointIteration, LogDEVSEvent, LogState, NextTime}
import org.mongodb.scala.bson.collection.immutable.Document
import org.mongodb.scala.{Completed, MongoClient, Observer}

import scala.util.parsing.json.JSONFormat

object MongodbLogger {
  def props(connectionString: String, initialTime: Duration = Duration.ofSeconds(0), typeRegistryOption: Option[JsonFormat.TypeRegistry] = None,
    designPoint: DesignPointIteration = SimLogger.buildDesignPointIteration(1,1),
            runTime: String = java.time.Instant.now().toString, runId: String = java.util.UUID.randomUUID().toString,
            databaseNameOption: Option[String] = None) = {
    Props(new MongodbLogger(connectionString, initialTime, typeRegistryOption, designPoint, runTime, runId, databaseNameOption))
  }
}

class MongodbLogger(connectionString: String, initialTime: Duration = Duration.ofSeconds(0), typeRegistryOption: Option[JsonFormat.TypeRegistry] = None,
                    private var designPoint: DesignPointIteration = SimLogger.buildDesignPointIteration(1,1),
                    val runTime: String = java.time.Instant.now().toString,
                    val runId: String = java.util.UUID.randomUUID().toString,
                    val databaseNameOption: Option[String] = None)
  extends Actor with MessageConverter with ActorLogging {

  val mongoClient = MongoClient(connectionString)
  val databaseName = databaseNameOption match {
    case Some(name) => name
    case None => context.system.name
  }
  val database = mongoClient.getDatabase(databaseName)
  val gson: Gson = new Gson()
  var currentTime = initialTime

  def buildStateInsert(time: java.time.Duration, actor: ActorRef, stateVariable: String, jsonState: String): String = {
    s"""{ runId: "$runId", runTime: "$runTime", designPoint: ${designPoint.getDesignPoint}, iteration: ${designPoint.getIteration} actor: "${actor.path.toString}", stateVariable: "$stateVariable", time: ${time.toMillis}, state: ${jsonState} }"""
  }

  def buildEventInsert(time: java.time.Duration, actor: ActorRef, eventType: String, eventVariable: String, jsonEvent: String) = {
    s"""{ runId: "$runId", runTime: "$runTime", designPoint: ${designPoint.getDesignPoint}, iteration: ${designPoint.getIteration} actor: "${actor.path.toString}", eventType: "$eventType", eventVariable: "$eventVariable", time: ${time.toMillis}, event: ${jsonEvent} }"""
  }

  def buildCompleted(collection: String): Observer[Completed] = {
    new Observer[Completed] {
      override def onNext(result: Completed): Unit = {}
      override def onError(e: Throwable): Unit = log.error(s"Error inserting to mongodb collection $collection: $e")
      override def onComplete(): Unit = {}
    }
  }

  def getSimpleName(a: Any): String = {
    val className = a.getClass.getSimpleName
    (className.contains(".") match {
      case true => className.substring(0, className.indexOf(".") - 1)
      case false => className
    }).replaceAllLiterally("$", "")
  }

  def insertJson(json: String, collection: String): Unit = {
    log.debug(s"Inserting to $collection: $json")
    database.getCollection(collection).insertOne(Document(json)).subscribe(buildCompleted(collection))
  }

  def logEvent(time: Duration, actor: ActorRef, eventType: String, eventVariable: String, event: Any): Unit = {
    val eventJson = gson.toJson(event)
    val insertEvent = buildEventInsert(time, actor, eventType, eventVariable, eventJson)
    insertJson(insertEvent, eventVariable)
  }

  def jsonFormat(message: Message): String = {
    typeRegistryOption match {
      case Some(typeRegistry) => JsonFormat.printer().usingTypeRegistry(typeRegistry).print(message)
      case None => JsonFormat.printer().print(message)
    }
  }


  def receive = {

    case nt: NextTime =>
      currentTime = Duration.parse(nt.getTimeString)

    case lde: LogDEVSEvent =>
      val event = convertMessage(lde.getEvent.getEventData, lde.getEvent.getJavaClass)
      val time = Duration.parse(lde.getEvent.getExecutionTimeString)
      val jsonEvent = jsonFormat(event)
      val eventVariable = lde.getModelName + "." + getSimpleName(event)
      val eventInsert = buildEventInsert(time, sender(), lde.getEvent.getEventType + "_EVENT", eventVariable, jsonEvent)
      insertJson(eventInsert, eventVariable)


    case LogExternalEvent(e, modelName,  timeOption) =>
      logEvent(timeOption.getOrElse(currentTime), sender(), "EXTERNAL_EVENT", modelName + "." + "event." + getSimpleName(e), e)

    case LogInternalEvent(e, modelName, timeOption) =>
      logEvent(timeOption.getOrElse(currentTime), sender(), "INTERNAL_EVENT", modelName + "." + "event." + getSimpleName(e), e)

    case LogOutputEvent( e, modelName, timeOption ) =>
      logEvent(timeOption.getOrElse(currentTime), sender(), "OUTPUT_EVENT", modelName + "." + "event." + getSimpleName(e), e)

    case ls: LogState =>
      val state = convertMessage(ls.getState, ls.getJavaClass)
      val jsonState = jsonFormat(state)
      val stateName = ls.getModelName + "." + ls.getVariableName
      //logString( ls.getVariableName + " : " + state, ls.getTimeInStateString )
      val jsonInsert = buildStateInsert(Duration.parse(ls.getTimeInStateString), sender(), stateName, jsonState)
      log.debug("LogState: " + jsonInsert)
      val collection = database.getCollection(stateName)
      insertJson(jsonState, stateName)

    case LogStateCase(name, modelName, state, timeOption) =>
      val jsonState = gson.toJson(state)
      val stateName = modelName + "." + getSimpleName(state)
      val jsonInsert = buildStateInsert(timeOption.getOrElse(currentTime), sender(), stateName, jsonState)
      log.debug("LogStateCase: " + jsonInsert)
      insertJson(jsonInsert, stateName)

  }



}
