/**
  * Copyright (C) 2014 TU Berlin (peel@dima.tu-berlin.de)
  *
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
  *
  *         http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  */
package org.peelframework.reef.beans.system

import com.samskivert.mustache.Mustache
import org.peelframework.core.beans.system.Lifespan._
import org.peelframework.core.beans.system.{SetUpTimeoutException, LogCollection, System}
import org.peelframework.core.config.{Model, SystemConfig}

import scala.util.matching.Regex

/** Wrapper class for Apache REEF.
  *
  * Implements REEF as a Peel `System` and provides setup and teardown methods.
  *
  * @param version Version of the system (e.g. "7.1")
  * @param configKey The system configuration resides under `system.\${configKey}`
  * @param lifespan `Lifespan` of the system
  * @param dependencies Set of dependencies that this system needs
  * @param mc The moustache compiler to compile the templates that are used to generate property files for the system
  */
class REEF(  version      : String,
             configKey    : String,
             lifespan     : Lifespan,
             dependencies : Set[System] = Set(),
             mc           : Mustache.Compiler) extends System("flink", version, configKey, lifespan, dependencies, mc)
with LogCollection {
  /** Returns an of the system configuration using the current Config */
  override protected def configuration(): SystemConfig = ???

  /** Starts up the system and polls to check whether everything is up.
    *
    * @throws SetUpTimeoutException If the system was not brought after {startup.pollingCounter} times {startup.pollingInterval} milliseconds.
    */
  override protected def start(): Unit = ???

  /** Stops the system. */
  override protected def stop(): Unit = ???

  /** Checks whether a process   for this system is already running.
    *
    * This is different from the value of `isUp`, as a system can be running, but not yet up and operational (i.e. if
    * not all worker nodes of a distributed have connected).
    *
    * @return True if a system process for this system exists.
    */
  override def isRunning: Boolean = ???


  /** The patterns of the log files to watch. */
  override protected def logFilePatterns(): Seq[Regex] = ???
}
