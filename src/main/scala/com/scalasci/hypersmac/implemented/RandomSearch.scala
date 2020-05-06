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
          .from((tIn.map(_.xElliptic).:+(0)).max + 1) //zero if no history
          .map(xElliptic => xElliptic -> renders.sample(space, xElliptic))
          .map {
            case (xElliptic, initial) =>
              Trial(
                initial,
                java.util.UUID.randomUUID().toString,
                None,
                budget,
                xElliptic
              )
          }

      val t = tInit.take(n)

      val sh = Future.sequence(t.map(sample => {
        println(s"running ${sample.configID} at budget ${sample.budget}")
        f(sample.config, budget).map { result =>
          Trial(
            sample.config,
            sample.configID,
            Some(result),
            budget,
            sample.xElliptic
          )
        }
      }))
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
