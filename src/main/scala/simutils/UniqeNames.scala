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

/**
  * This trait is used by actors for actor naming.  It increments an integer to be appended to names
  */
trait UniqueNames {
  var nameCount: Long = 0

  /**
    * Utility method to append a uniquely incremental long value to a name
    * @param base  The base name
    * @return  The name with a Long value appended
    */
  def uniqueName(base: String): String = {
    nameCount = nameCount + 1
    base + (nameCount - 1)
  }
}
