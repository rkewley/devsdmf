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

import java.time._

object TimeUtil {
  val NANOSPERSEC = 1000000000
  def timeToDouble(seconds: Long, nanoseconds: Int): Double = seconds + nanoseconds/NANOSPERSEC

  def timeToDouble(duration: Duration): Double = timeToDouble(duration.getSeconds, duration.getNano)

  def timeToDouble(instant: Instant): Double = timeToDouble(instant.getEpochSecond, instant.getNano)

  def doubleSecondsToDuration(seconds: Double): Duration = {
    val floor: Double = seconds.floor
    val nanos: Long = ((seconds - floor) * NANOSPERSEC).toLong
    Duration.ofSeconds(floor.toLong).plus(Duration.ofNanos(nanos))
  }

  val InfiniteTime = Duration.ofSeconds(Long.MaxValue)

}

class TimeUtil(val startTime: Instant) {
  import TimeUtil._

  def durationToUTC(duration: Duration): Double = {
    timeToDouble(startTime) + timeToDouble(duration)
  }

}
