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

  def props(fileName:String, fmt: Format = new BasicFormat) = Props( new FileLogger( fileName, fmt ) )
}

/**  Format trait
  *
  *  Allow customization of how messages are written to a file.
  * */
trait Format {
  def begin(): Option[String]

  def end(): Option[String]

  def format( a: Any ): Option[String]
}

class BasicFormat extends Format {
  def begin(): Option[String] = None

  def end(): Option[String] = None

  def format( a: Any ): Option[String] = a match {
    case s: String => Some(s)
    case _ => None
  }
}

/**
  * A class designed to log messages to a file.  A Format object may
  * be provided which allows custom begin and end block and converts
  * messages to String.  Be default, only String messages are written
  * to the file.
  *
  * @param fileName  The filename to which log messages are written
  */
class FileLogger(val fileName: String, val fmt: Format = new BasicFormat ) extends Actor {
  var closed = false
  val pw = new PrintWriter(new File(fileName))
  fmt.begin.map( s => write(s) )

  def receive = {
    case c: CloseFile =>
      close()
      sender ! FileLogger.buildFileClosed

    case a: Any =>
      fmt.format( a ).map( s => write(s) )
  }

  /**
    * Makes sure output file closed and flushed when actor stops.
    */
  override def postStop() {
    // No harm in calling this twice.
    close()
  }

  private def close() {
    if( !closed ) {
      fmt.end.map( s => write( s ) )
      pw.close()
      closed = true
    }
  }

  private def write( s: String ) {
    try {
      pw.write( s )
    }
    catch {
      case e: Exception =>
        println("Could not write to file: " + fileName)
    }
  }
}
