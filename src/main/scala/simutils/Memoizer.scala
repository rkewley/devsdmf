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

import scala.collection.concurrent.{Map => CMap}
import java.util.concurrent._
import java.util.concurrent.atomic.AtomicInteger
import scala.collection.JavaConverters._

/** Memoization class to handle caching results of expensive function.
 *
 * Provide capability to memoize return values for functions.  The cache is
 * concurrent and stores futures, instead of of acutal values, to avoid
 * unnecessary duplicate processing.  Instances of this memoizer also allow
 * specifying maximum number of entries to cache.
 * */
class Memoizer[K, V](func: K => V, MaxDataPoolEntries: Integer = 25) {

  private val dataIndex = new AtomicInteger(0)
  private val dataPools: Array[ CMap[K, Future[V]] ] = Array[ CMap[K, Future[V]] ](
    new ConcurrentHashMap().asScala,
    new ConcurrentHashMap().asScala )

  /** Access a value from cache, and if nto present, load it.
    *
    * @param key: The value lookup key
    */
  def apply ( key: K ): V = {
    val index: Integer = dataIndex.get()
    while(true) {
      val ret =
        dataPools(index).get( key ).getOrElse (
          dataPools((index+1)%2).get( key ).map(value => putDataCache( index, key, value  ) ).getOrElse {
            val eval = new Callable[V] { def call(): V = func(key) }
            val ft =  new FutureTask[V](eval)
            putDataCache( index, key, ft ) match {
              case f if f == ft =>
                ft.run
                ft
              case f => f
            }
          })

      try{
        return ret.get
      } catch {
        case e: CancellationException => {
          dataPools(index).remove( key, ret )
          dataPools((index+1)%2).remove( key, ret )
        }
        case e: ExecutionException =>
          throw e.getCause
      }
    }
    // This line should never execute
    sys.error("Failed to compute result.")
  }

  /**  Place data in the cache pools, swapping pools if size threshold met.
   */
  private def putDataCache( index: Integer, key:K, value: Future[V] ): Future[V] = {
    dataPools(index).putIfAbsent( key, value ) match {
      case Some( oldValue )  => oldValue
      case None =>
        // Swap the data pools and clear out old one if size threshold met
        if( MaxDataPoolEntries < dataPools(index).size ) {
          synchronized {
            val currentIndex = dataIndex.get()
            // Recheck another therad has not already handled swap
            if( index == currentIndex
               && MaxDataPoolEntries < dataPools(index).size ) {
              val newIndex = (index + 1) % 2
              dataIndex.set( newIndex )
              dataPools(newIndex).clear()
            }
          }
        }
        value
    }
  }
}
