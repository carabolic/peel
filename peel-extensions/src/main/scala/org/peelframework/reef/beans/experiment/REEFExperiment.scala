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
package org.peelframework.reef.beans.experiment

import java.io.FileWriter
import java.nio.file.{Paths, Files}

import com.typesafe.config.Config
import org.peelframework.core.beans.data.{ExperimentOutput, DataSet}
import org.peelframework.core.beans.experiment.Experiment
import org.peelframework.core.beans.experiment.Experiment.Run
import org.peelframework.core.beans.system.System
import org.peelframework.core.util.shell
import org.peelframework.hadoop.beans.system.Yarn
import org.peelframework.reef.beans.experiment.REEFExperiment.SingleJobRun
import spray.json._

/**
  * An `Experiment` implementation that handles the execution of a single REEF job.
  */
class REEFExperiment(command: String,
                     systems: Set[System],
                     runner : Yarn,
                     runs   : Int,
                     inputs : Set[DataSet],
                     outputs: Set[ExperimentOutput],
                     name   : String,
                     config : Config) extends Experiment(command, systems, runner, runs, inputs, outputs, name, config) {

  /** Experiment run factory method.
    *
    * @param id The `id` for the constructed experiment run
    * @param force Force execution of this run
    * @return An run for this experiment identified by the given `id`
    */
  override def run(id: Int, force: Boolean): Run[Yarn] = new SingleJobRun(id, this, force)

  /** Copy the object with updated name and config values.
    *
    * @param name The updated name value.
    * @param config The updated config value.
    */
  override def copy(name: String, config: Config): Experiment[Yarn] =
    new REEFExperiment(command, systems, runner, runs, inputs, outputs, name, config)
}

object REEFExperiment {

  case class REEFState( runnerID: String,
                        runnerName: String,
                        runnerVersion: String,
                        var runTime: Long = 0,
                        var runExitCode: Option[Int] = None) extends Experiment.RunState

  object REEFStateProtocol extends DefaultJsonProtocol with NullOptions {
    implicit val reefStateFormat = jsonFormat5(REEFState)
  }

  class SingleJobRun( val id: Int,
                      val exp: REEFExperiment,
                      val force: Boolean) extends Experiment.SingleJobRun[Yarn, REEFState] {

    import REEFStateProtocol._

    override protected def loadState(): REEFState = {
      if (Files.isRegularFile(Paths.get(s"$home/state.json"))) {
        try {
          scala.io.Source.fromFile(s"$home/state.json").mkString.parseJson.convertTo[REEFState]
        } catch {
          case e: Throwable => REEFState(exp.runner.beanName, exp.runner.name, exp.runner.version)
        }
      } else {
        REEFState(exp.runner.beanName, exp.runner.name, exp.runner.version)
      }
    }

    override protected def writeState(): Unit = {
      val fw = new FileWriter(s"$home/state.json")
      fw.write(state.toJson.prettyPrint)
      fw.close()
    }

    override protected def runJob(): Unit = {
      val (exitCode, runTime) = Experiment.time(this !(s"${command.trim}", s"$home/run.out", s"$home/run.err"))
      state.runExitCode = Some(exitCode)
      state.runTime = runTime
    }

    override protected def cancelJob(): Unit = {} // NOP - do nothing

    /** Check if the execution of this run exited successfully. */
    override def isSuccessful: Boolean = state.runExitCode.getOrElse(-1) == 0

    private def !(command: String, outFile: String, errFile: String) = {
      val yarnHome = exp.config.getString(s"system.${exp.runner.configKey}.path.home")
      // get the yarn classpath, such that REEF knows about
      val yarnClasspath = (shell !! s"$yarnHome/bin/yarn classpath").trim
      // add the $BUNDLE_BIN/apps directory
      val jarHome = exp.config.getString("app.path.apps")
      val libDir = s"${exp.config.getString("app.path.home")}/lib"
      shell ! s"java -cp $yarnClasspath:$libDir/*:$jarHome/* ${command.trim} > $outFile 2> $errFile"
    }
  }
}
