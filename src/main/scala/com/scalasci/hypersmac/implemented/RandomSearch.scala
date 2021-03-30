package com.scalasci.hypersmac.implemented

import com.scalasci.hypersmac.api.{Optimizer, RendersAndSamplesConfig}
import com.scalasci.hypersmac.model.{TrialSetup, TrialWithResult}

import scala.concurrent.{ExecutionContext, Future}

object RandomSearch {

  def apply[ConfigSpace, ConfigSample](budget: Double,
                                       iterations: Int = 100)(
                                        implicit renders: RendersAndSamplesConfig[ConfigSpace, ConfigSample],
                                        ec: ExecutionContext
                                      ): Optimizer[ConfigSpace, ConfigSample] =
    (space: ConfigSpace, f: BudgetedSampleFunction[ConfigSample], note:Option[String]) => {
      //  A state-aggregating recursive run
      def innerLoop(remainingIterations: Int = iterations,
                    tIn: Seq[TrialWithResult[ConfigSample]] = Seq.empty //these are included to condition smac.
                   ): Future[Seq[TrialWithResult[ConfigSample]]] = {
        val n = 1

        //  generate the candidate configs randomly
        def tInit: Iterator[TrialSetup[ConfigSample]] =
          Iterator
            .from(tIn.map(_.setup.xElliptic).:+(0).max + 1) //zero if no history
            .map(xElliptic => xElliptic -> renders.sample(space, xElliptic))
            .map {
              case (xElliptic, initial) =>
                TrialSetup(
                  initial,
                  java.util.UUID.randomUUID().toString,
                  budget,
                  xElliptic,
                  note = note
                )
            }

        val t = tInit.take(n)

        val newResults = f.evaluate(t.toSeq, budget).map(nr => tIn ++ nr)

        if (remainingIterations > 0) {
          newResults.flatMap(
            results => innerLoop(remainingIterations - 1,
              results
            )
          )
        } else {
          newResults
        }
      }

      innerLoop()
    }
}
