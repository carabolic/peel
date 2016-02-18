package org.peelframework.reef.beans.experiment

import com.typesafe.config.Config
import org.peelframework.core.beans.data.{ExperimentOutput, DataSet}
import org.peelframework.core.beans.experiment.Experiment
import org.peelframework.core.beans.experiment.Experiment.Run
import org.peelframework.core.beans.system.System
import org.peelframework.reef.beans.experiment.REEFExperiment.SingleJobRun
import org.peelframework.reef.beans.system.REEF

/**
  * Created by carabolic on 18/02/16.
  */
class REEFExperiment(command: String,
                     systems: Set[System],
                     runner : REEF,
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
  override def run(id: Int, force: Boolean): Run[REEF] = new SingleJobRun(id, this, force)

  /** Copy the object with updated name and config values.
    *
    * @param name The updated name value.
    * @param config The updated config value.
    */
  override def copy(name: String, config: Config): Experiment[REEF] =
    new REEFExperiment(command, systems, runner, runs, inputs, outputs, name, config)
}

object REEFExperiment {

  case class REEFState( runnerID: String,
                        runTime: Long,
                        runExitCode: Option[Int],
                        runnerVersion: String,
                        runnerName: String) extends Experiment.RunState

  class SingleJobRun( val id: Int,
                      val exp: REEFExperiment,
                      val force: Boolean) extends Experiment.SingleJobRun[REEF, REEFState] {

    override protected def loadState(): REEFState = ???

    override protected def writeState(): Unit = ???

    override protected def runJob(): Unit = ???

    override protected def cancelJob(): Unit = ???

    /** Check if the execution of this run exited successfully. */
    override def isSuccessful: Boolean = ???
  }
}
