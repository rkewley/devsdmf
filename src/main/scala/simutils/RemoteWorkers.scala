package simutils

import akka.actor._

import scala.collection.mutable.ListBuffer

object RemoteWorkers {
  case class NewRemoteWorker(address: Address)

  case class RemoteWorkersReady(addresses: List[Address])
  /**
    * Factory method for [[akka.actor.Props]] creation for [[RemoteWorkers]]
    *
    * @param readyActor  Actor to notify when workers are ready
    * @param minWorkers  Number of worker required before [[RemoteWorkers]] is ready
    * @return [[akka.actor.Props]] for [[RemoteWorkers]] [[akka.actor.Actor]] creation
    */
  def props(readyActor: ActorRef, minWorkers: Int) = Props(new RemoteWorkers(readyActor, minWorkers))
}


class RemoteWorkers(val readyActor: ActorRef, val minWorkers: Int = 1) extends Actor {
  val workers = ListBuffer[Address]()
  var index = 0
  var started = false

  import RemoteWorkers._
  def receive = {
    case NewRemoteWorker(address) =>
      println("Adding " + address + " to workers list")
      workers += address
      if (workers.size == minWorkers && !started) {
        started = true
        readyActor ! RemoteWorkersReady(workers.toList)
        self ! PoisonPill
      }
  }
}

