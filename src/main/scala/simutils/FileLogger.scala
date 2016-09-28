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
import akka.actor.{Actor, Props}
import dmfmessages.DMFSimMessages._

object FileLogger {
  def buildCloseFile: CloseFile = CloseFile.newBuilder().build()
  def buildFileClosed: FileClosed = FileClosed.newBuilder().build()

  def props(fileName:String) = Props( new FileLogger( fileName ) )
}

/**
  * A class designed to log simulation messages.  The [[devsmodel.RootCoordinator]] sends messages to this logger
  * to keep the current time updated.  Any actor in the simulation can then log messages received and state data to this
  * logger with time parameters.
 *
  * @param fileName  The filename to which log messages are written
  */
class FileLogger(val fileName: String) extends Actor {
  val pw = new PrintWriter(new File(fileName))

  def receive = {
    case s: String =>
      try {
        pw.write( s )
      }
      catch {
        case e: Exception =>
          println("Could not write to file: " + fileName)
      }

    case c: CloseFile =>
      pw.close()
      sender ! FileLogger.buildFileClosed
  }

  /**
    * Makes sure output file closed and flushed when actor stops.
    */
  override def postStop() {
    // No harm in calling this twice.
    pw.close()
  }
}
