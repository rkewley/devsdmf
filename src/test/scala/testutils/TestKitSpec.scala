package testutils

import akka.actor.{Actor, ActorRef, ActorSystem}
import akka.testkit.{ ImplicitSender, TestKit }
import org.scalatest.{WordSpecLike, Matchers, BeforeAndAfterAll}

abstract class TestKitSpec(name: String)
  extends TestKit(ActorSystem(name))
  with WordSpecLike
  with Matchers
  with BeforeAndAfterAll
  with ImplicitSender {

  override def afterAll() {
    system.shutdown()
  }
}


class Wrapper(target: ActorRef) extends Actor {
  def receive = {
    case x => target forward x
  }
}
