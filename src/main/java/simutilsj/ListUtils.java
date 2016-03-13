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

package simutilsj;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class ListUtils {
	
	public static <T> List<T> copyRemoveFirst(List<T> list) {
		Iterator<T> it = list.iterator();
		LinkedList<T> newList = new LinkedList<T>();
		if (it.hasNext()) {
			it.next();  // skip the first item
			while (it.hasNext()) {
				newList.add(it.next());
			}
		}
		return newList;
	}

	public static <T> List<T> copyList(List<T> list) {
		Iterator<T> it = list.iterator();
		LinkedList<T> newList = new LinkedList<T>();
			while (it.hasNext()) {
				newList.add(it.next());
			}
		return newList;
	}

}
