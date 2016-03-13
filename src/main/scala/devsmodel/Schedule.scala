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

import simutils.TimeManager

import scala.collection.immutable.{TreeSet, TreeMap}

class ScheduleItem(val executionTime: Duration, val events: TreeSet[DEVSEvent[_ <: Serializable]])

class Schedule(initialTime: Duration = Duration.ofSeconds(0),
               private var schedule: TreeMap[Duration, TreeSet[DEVSEvent[_ <: Serializable]]] = TreeMap[Duration, TreeSet[DEVSEvent[_ <: Serializable]]]())  {


    def addEvent(event: DEVSEvent[_ <: Serializable]) = {
      val time = event.executionTime
      val treeSetOption = schedule.get(time)
      val newTreeSet = treeSetOption match {
        case Some(treeSet) => treeSet + event
        case None => TreeSet[DEVSEvent[_ <: Serializable]](event)
      }
      schedule += time -> newTreeSet
      //schedule += (time -> (event + (schedule get time getOrElse Nil)))
    }

    def getEventAt(time: Duration) = schedule.get(time)

    def getNextScheduleTime: Duration = {
      val nextEvents = schedule.headOption
      nextEvents match {
        case Some(events) => events._1
        case None => TimeManager.infiniteTime
      }
    }

    def removeEventsAt(time: Duration): TreeSet[DEVSEvent[_ <: Serializable]] = {
      schedule.get(time) match {
        case Some(eventList) =>
          schedule -= time
          eventList
        case None => TreeSet[DEVSEvent[_ <: Serializable]]()
      }
    }

    def removeNextSingleEvent: Unit = {
      val nextEvents: Option[(Duration, TreeSet[DEVSEvent[_ <: Serializable]])] = schedule.headOption
      nextEvents match {
        case Some((time, eventList)) => {
          eventList.size match {
            case 1 =>
              schedule -= time
            case x if x > 1 =>
              schedule += (time -> eventList.tail)
            case _ =>
              throw new SynchronizationException("There are no events for the current schedule item")
          }
        }
        case None => None
      }
    }

    def removeNextEvents: Unit = {
      val nextEvents: Option[(Duration, TreeSet[DEVSEvent[_ <: Serializable]])] = schedule.headOption
      nextEvents match {
        case Some((time, eventList)) =>
          schedule -= time
        case None => None
      }
    }

    def getNextEvents: Option[ScheduleItem] = {
      val nextEvents: Option[(Duration, TreeSet[DEVSEvent[_ <: Serializable]])] = schedule.headOption
      nextEvents match {
        case Some((time, eventList)) =>
          Some(new ScheduleItem(time, eventList))
        case None => None
      }
    }

  def getNextSingleEvent: Option[DEVSEvent[_]] = {
    val nextEvents: Option[(Duration, TreeSet[DEVSEvent[_ <: Serializable]])] = schedule.headOption
    nextEvents match {
      case Some((time, eventList)) =>
        Some(eventList.head)
      case None => None
    }
  }

  def getAndRemoveNextEvents: Option[ScheduleItem] = {
    val nextEvents: Option[(Duration, TreeSet[DEVSEvent[_ <: Serializable]])] = schedule.headOption
    nextEvents match {
      case Some((time, eventList)) => {
        schedule -= time
        Some(new ScheduleItem(time, eventList))
      }
      case None => None
    }
  }

  def getAndRemoveNextSingleEvent: Option[DEVSEvent[_]] = {
    val nextEvent = getNextSingleEvent
    removeNextSingleEvent
    nextEvent
  }

  def getFirstEventAt(time: Duration): Option[DEVSEvent[_]] = {
    getEventAt(time).map(_.head)
  }


    def deleteAllEvents = {
      schedule = TreeMap[Duration, TreeSet[DEVSEvent[_ <: Serializable]]]()

    }

}
