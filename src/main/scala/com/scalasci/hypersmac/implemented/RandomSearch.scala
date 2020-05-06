package com.scalasci.hypersmac.implemented

import com.scalasci.hypersmac.api.RendersConfig
import com.scalasci.hypersmac.model.Trial

import scala.concurrent.{ExecutionContext, Future}

object RandomSearch {

  def apply[ConfigSpace, ConfigSample](space: ConfigSpace,
                                       f: BudgetedSampleFunction[ConfigSample],
                                       budget: Double,
                                       iterations: Int = 100)(
    implicit renders: RendersConfig[ConfigSpace, ConfigSample],
    ec: ExecutionContext
  ): Future[Seq[Trial[ConfigSample]]] = {

    def innerLoop(
      remainingIterations: Int = iterations,
      tIn: Seq[Trial[ConfigSample]] = Seq.empty //these are included to condition smac.
    ): Future[Seq[Trial[ConfigSample]]] = {
      val n = 1
      def tInit: Iterator[Trial[ConfigSample]] =
        Iterator
          .from(0)
          .map(xElliptic => xElliptic -> renders.sample(space, xElliptic))
          .map {
            case (xElliptic, initial) =>
              Trial(
                initial,
                java.util.UUID.randomUUID().toString,
                None,
                0.0,
                xElliptic
              )
          }

      val t = if (tIn.isEmpty) {
        tInit.take(n)
      } else {
        tInit.take(n)
      }
      val sh = Future.sequence(
        t.map(
          sample =>
            f(sample.config, budget).map { result =>
              Trial(sample.config, sample.configID, Some(result), budget, 0)
          }
        )
      )
      val newResults = if (remainingIterations >= 0) {
        sh.flatMap { results =>
          Future(tIn ++ results)
        }
      } else {
        sh
      }

      newResults.map(nr => tIn ++ nr)
    }
    innerLoop()
  }
}
