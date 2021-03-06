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
package org.peelframework.core.cli.command.experiment

import java.lang.{System => Sys}

import org.peelframework.core.beans.experiment.ExperimentSuite
import org.peelframework.core.beans.system.{Lifespan, System}
import org.peelframework.core.cli.command.Command
import org.peelframework.core.config.{Configurable, loadConfig}
import org.peelframework.core.graph.{Node, createGraph}
import org.peelframework.core.util.console._
import net.sourceforge.argparse4j.impl.Arguments
import net.sourceforge.argparse4j.inf.{Namespace, Subparser}
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Service

/** Execute a specific experiment. */
@Service("exp:run")
class Run extends Command {

  override val name = "exp:run"

  override val help = "execute a specific experiment"

  override def register(parser: Subparser) = {
    // options
    parser.addArgument("--just")
      .`type`(classOf[Boolean])
      .dest("app.suite.experiment.just")
      .action(Arguments.storeTrue)
      .help("skip system set-up and tear-down")
    parser.addArgument("--run")
      .`type`(classOf[Integer])
      .dest("app.suite.experiment.run")
      .metavar("RUN")
      .help("run to execute")
    // arguments
    parser.addArgument("suite")
      .`type`(classOf[String])
      .dest("app.suite.name")
      .metavar("SUITE")
      .help("suite containing the experiment")
    parser.addArgument("experiment")
      .`type`(classOf[String])
      .dest("app.suite.experiment.name")
      .metavar("EXPERIMENT")
      .help("experiment to run")

    // option defaults
    parser.setDefault("app.suite.experiment.run", 1)
  }

  override def configure(ns: Namespace) = {
    // set ns options and arguments to system properties
    Sys.setProperty("app.suite.experiment.just", if (ns.getBoolean("app.suite.experiment.just")) "true" else "false")
    Sys.setProperty("app.suite.experiment.run", ns.getInt("app.suite.experiment.run").toString)
    Sys.setProperty("app.suite.name", ns.getString("app.suite.name"))
    Sys.setProperty("app.suite.experiment.name", ns.getString("app.suite.experiment.name"))
  }

  override def run(context: ApplicationContext) = {
    val suiteName = Sys.getProperty("app.suite.name")
    val expName = Sys.getProperty("app.suite.experiment.name")
    val expRun = Sys.getProperty("app.suite.experiment.run").toInt
    val justRun = Sys.getProperty("app.suite.experiment.just") == "true"

    logger.info(s"Running experiment '${Sys.getProperty("app.suite.experiment.name")}' from suite '${Sys.getProperty("app.suite.name")}'")

    val suite = context.getBean(Sys.getProperty("app.suite.name"), classOf[ExperimentSuite])
    val graph = createGraph(suite)

    //TODO check for cycles in the graph
    if (graph.isEmpty) throw new RuntimeException("Experiment suite is empty!")

    // find experiment
    val exps = suite.experiments.filter(_.name == expName)
    // load config
    for (e <- suite.experiments.filter(_.name == expName)) e.config = loadConfig(graph, e)
    // check if experiment exists (the list should contain exactly one element)
    if (exps.size != 1) throw new RuntimeException(s"Experiment '$expName' either not found or ambigous in suite '$suiteName'")

    for (exp <- exps; r <- Some(exp.run(expRun, force = true))) {

      val allSystems = for (n <- graph.reverse.traverse(); if graph.descendants(exp).contains(n)) yield n
      val inpSystems: Set[Node] = for (in <- exp.inputs; sys <- in.dependencies) yield sys
      val expSystems = (graph.descendants(exp, exp.inputs) diff Seq(exp)).toSet

      try {
        logger.info("Executing experiment '%s'".format(exp.name))

        // update config
        for (n <- graph.descendants(exp)) n match {
          case s: Configurable => s.config = exp.config
          case _ => Unit
        }

        if (!justRun) {
          logger.info("Setting up / updating systems required for input data sets")
          for (n <- inpSystems) n match {
            case s: System => if (s.isUp) s.update() else s.setUp()
            case _ => Unit
          }

          logger.info("Materializing experiment input data sets")
          for (n <- exp.inputs; path = n.resolve(n.path)) if (!n.fs.exists(path)) {
            try {
              n.materialize()
            } catch {
              case e: Throwable => n.fs.rmr(path); throw e // make sure the path is cleaned for the next try
            }
          } else {
            logger.info(s"Skipping already materialized path '$path'".yellow)
          }

          logger.info("Tearing down redundant systems before conducting experiment runs")
          for (n <- inpSystems diff expSystems) n match {
            case s: System if !(Lifespan.PROVIDED :: Lifespan.SUITE :: Nil contains s.lifespan) => s.tearDown()
            case _ => Unit
          }

          logger.info("Setting up systems with SUITE lifespan")
          for (n <- allSystems) n match {
            case s: System if s.lifespan == Lifespan.SUITE && !s.isUp => s.setUp()
            case _ => Unit
          }

          logger.info("Updating systems with PROVIDED lifespan")
          for (n <- allSystems) n match {
            case s: System if s.lifespan == Lifespan.PROVIDED => s.update()
            case _ => Unit
          }

          logger.info("Setting up systems with EXPERIMENT lifespan")
          for (n <- expSystems) n match {
            case s: System if s.lifespan == Lifespan.EXPERIMENT => s.setUp()
            case _ => Unit
          }

        } else {
          logger.info("Updating all systems")
          for (n <- allSystems) n match {
            case s: System => s.update()
            case _ => Unit
          }
        }

        logger.info("Setting up systems with RUN lifespan")
        for (n <- allSystems) n match {
          case s: System if s.lifespan == Lifespan.RUN => s.setUp()
          case _ => Unit
        }

        for (n <- exp.outputs) n.clean()
        r.execute() // run experiment
      } catch {
        case e: Throwable =>
          logger.error(s"Exception for experiment ${exp.name} in suite ${suite.name}: ${e.getMessage}".red)
          throw e

      } finally {
        if (!justRun) {
          logger.info("Tearing down systems with SUITE or EXPERIMENT lifespan")
          for (n <- allSystems) n match {
            case s: System if Lifespan.SUITE :: Lifespan.EXPERIMENT :: Nil contains s.lifespan => s.tearDown()
            case _ => Unit
          }
        }

        logger.info("Tearing down systems with RUN lifespan")
        for (n <- allSystems) n match {
          case s: System if s.lifespan == Lifespan.RUN => s.tearDown()
          case _ => Unit
        }
      }
    }

  }
}
