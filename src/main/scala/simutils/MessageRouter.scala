package simutils

import akka.actor._
import dmfmessages.DMFSimMessages._
import scala.collection.mutable.ListBuffer

/**
  * Companion object for [[MessageRouter]]
  */
object MessageRouter {

  def buildWorkersReady: WorkersReady = WorkersReady.newBuilder().build
  def buildNewWorker: NewWorker = NewWorker.newBuilder().build
  def buildGotMessage: GotMessage = GotMessage.newBuilder().build()

  /**
    * Factor method for [[Props]] creation for [[MessageRouter]]
    *
    * @param readyActor  Actor to notify when workers are ready
    * @param minWorkers  Number of worker required before [[MessageRouter]] is ready
    * @return [[Props]] for [[MessageRouter]] [[Actor]] creation
    */
  def props(readyActor: ActorRef, minWorkers: Int) = Props(new MessageRouter(readyActor, minWorkers))

}

/**
  * An [[Actor]] that first awaits creation of [[minWorkers]] workers.  Then forwards messages round robin to those workders.
  *
  * @param readyActor  Actor to notify when [[minWorkers]] are ready
  * @param minWorkers  Then numberr or workers needed before being ready
  */
class MessageRouter(val readyActor: ActorRef, val minWorkers: Int = 1) extends Actor {
  val workers = new ListBuffer[ActorRef]()
  var index = 0
  var started = false
  context.become(awaitWorkers)

  import MessageRouter._

  /**
    * Uponn receipt of a [[NewWorker]] message, add thatworker to the [[workers]] [[ListBuffer]]
    * Upon receipt of a [[GotMessage]] message, print a debugging message
    * Upon receipt of any other message, forward it to the next worker on the [[workers]] [[ListBuffer]] and increment
    * the [[index]]
    */
  def awaitWorkers: Receive = {
    case n: NewWorker =>
      println("Adding " + sender() + " to workers list")
      workers += sender()
      if (workers.size == minWorkers && !started) {
        started = true
        readyActor ! MessageRouter.buildWorkersReady
        context.unbecome()
      }
  }

  def receive = {
    case g: GotMessage => println(sender() + " got a message")
    case message: Any =>
      workers.size match {
        case 0 => println("No workers")
        case _ =>
          index = index match {
            case x if x == workers.size - 1 => 0
            case _ => index + 1
          }
          workers(index).forward(message)
      }
  }
}

