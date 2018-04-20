package simutils

import akka.actor._
import dmfmessages.DMFSimMessages._
import scala.collection.mutable.ListBuffer
import scala.collection.JavaConversions._

object RemoteWorkers {
  def translateAkkaAddres(akkaAddress: AkkaAddress): Address = Address(akkaAddress.getProtocol, akkaAddress.getSystem, akkaAddress.getHost, akkaAddress.getPort)
  def buildAkkaAddress(address: Address): AkkaAddress = AkkaAddress.newBuilder()
    .setProtocol(address.protocol).setSystem(address.system).setHost(address.host.getOrElse("")).setPort(address.port.getOrElse(0)).build
  def buildRemoteWorkersReady(addresses: Seq[Address]): RemoteWorkersReady =  {
    val addressList = addresses.map (a => buildAkkaAddress(a))
    RemoteWorkersReady.newBuilder().addAllAddresses(addressList).build
  }
  def buildNewRemoteWorker(address: Address) = NewRemoteWorker.newBuilder().setAddress(buildAkkaAddress(address)).build

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
    case nrw: NewRemoteWorker =>
      val addr = nrw.getAddress
      val address = Address(addr.getProtocol, addr.getSystem, addr.getHost, addr.getPort)
      println("Adding " + address + " to workers list")
      workers += address
      if (workers.size == minWorkers && !started) {
        started = true
        readyActor ! buildRemoteWorkersReady(workers.toList)
        self ! PoisonPill
      }
  }
}

