package com.scalasci.hypersmac.implemented

import com.scalasci.hypersmac.api.RendersAndSamplesConfig
import com.scalasci.hypersmac.model.Trial

import scala.concurrent.{ExecutionContext, Future}

object RandomSearch {

  def apply[ConfigSpace, ConfigSample](space: ConfigSpace,
                                       f: BudgetedSampleFunction[ConfigSample],
                                       budget: Double,
                                       iterations: Int = 100)(
    implicit renders: RendersAndSamplesConfig[ConfigSpace, ConfigSample],
    ec: ExecutionContext
  ): Future[Seq[Trial[ConfigSample]]] = {

    //  A state-aggregating recursive run
    def innerLoop(
      remainingIterations: Int = iterations,
      tIn: Seq[Trial[ConfigSample]] = Seq.empty //these are included to condition smac.
    ): Future[Seq[Trial[ConfigSample]]] = {
      val n = 1
      //  generate the candidate configs randomly
      def tInit: Iterator[Trial[ConfigSample]] =
        Iterator
          .from(tIn.map(_.xElliptic).:+(0).max + 1) //zero if no history
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

      val newResults = Future
        .sequence(
          t.map(sample => {
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
            })
            .toSeq
        )
        .map(nr => tIn ++ nr)

      if (remainingIterations > 0) {
        newResults.flatMap(
          results => innerLoop(remainingIterations - 1, results)
        )
      } else {
        newResults
      }
    }
    innerLoop()
  }
}
