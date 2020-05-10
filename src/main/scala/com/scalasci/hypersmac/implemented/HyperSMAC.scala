package com.scalasci.hypersmac.implemented

import com.scalasci.hypersmac.api.RendersAndSamplesConfig
import com.scalasci.hypersmac.model.Trial

import scala.concurrent.{ExecutionContext, Future}

trait BudgetedSampleFunction[ConfigSample]
    extends ((ConfigSample, Double) => Future[Double])

trait HyperSMAC[ConfigSpace, ConfigSample] {

  def trainSurrogateModel(
    history: Seq[Trial[ConfigSample]]
  ): ConfigSample => Boolean

  /**
    * Run HyperSmac
    * @param space The parameter space from which to sample configurations
    * @param f the loss function. parameters will be optimized to minimize this value
    * @param eta the hyperband eta parameter to use on inner runs
    * @param R the hyperband R parameter
    * @param runs number of inner hyperband runs. setting this greater than one will allow smac optimization
    * @param history a resume history, empty if initializing.
    * @param renders means of converting a sample from the parameter space into a double array, a smac feature vector
    * @param ec an execution context
    * @return all of the completed trials. feeding this back into the function will resume optimization.
    */
  def apply(space: ConfigSpace,
            f: BudgetedSampleFunction[ConfigSample],
            eta: Double = 3.0,
            R: Double = 20.0,
            runs: Int = 1,
            history: Seq[Trial[ConfigSample]] = Seq.empty)(
    implicit renders: RendersAndSamplesConfig[ConfigSpace, ConfigSample],
    ec: ExecutionContext
  ): Future[Seq[Trial[ConfigSample]]] = {
    //calculate HyperBand constants
    val sMax = math.floor(math.log(R) / math.log(eta)).toInt
    val b = (sMax + 1) * R

    // successive calls to inner loop will condition space sample on previous good samples smac filter.
    def innerLoop(
      s: Int = sMax,
      historyInner: Seq[Trial[ConfigSample]] = history, //these are included to condition smac.
      remainingRuns: Int = runs
    ): Future[Seq[Trial[ConfigSample]]] = {
      val r = R * math.pow(eta, -s) // largest resources at smallest s
      val n = math.ceil(b * math.pow(eta, s) / r).toInt

      val t = {
        val chooser = trainSurrogateModel(historyInner)
        val tInit: Iterator[Trial[ConfigSample]] =
          Iterator
            .from(historyInner.map(_.xElliptic).:+(-1).max + 1) //zero if no history
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

        tInit.filter(sample => chooser(sample.config)).take(n)
      }

      val sh = runSucessiveHalving(f, t.toSeq, s, n, eta, r)
      val newResults = if (s >= 0) {
        sh.flatMap { results =>
          innerLoop(s - 1, historyInner ++ results, runs)
        }
      } else if (remainingRuns < 0) {
        sh.flatMap { results =>
          innerLoop(sMax, historyInner ++ results, runs - 1)
        }
      } else {
        Future(historyInner)
      }

      newResults
    }

    // recursively runs the optimization for the specified number of runs.
    innerLoop()
  }

  private def runSucessiveHalving(
    f: BudgetedSampleFunction[ConfigSample],
    samples: Seq[Trial[ConfigSample]],
    iMax: Int,
    n: Int,
    eta: Double,
    r: Double,
    i: Int = 0
  )(implicit ec: ExecutionContext): Future[Seq[Trial[ConfigSample]]] = {

    // number of sub-runs for this iteration
    val ni = n * math.pow(eta, -i)

    //  resource allotment for each sub-run
    val ri = r * math.pow(eta, i)

    val contenders =
      samples // this might resume a low-budget run from history if it's good.
        .filter(_.budget <= ri) // ...as long as this SH budget is higher than the candidate's max.
        .sortBy(_.cost.getOrElse(Double.MaxValue))
        .take(math.floor(ni / eta).toInt) // we will resume the "ni" most promising-looking candidates remaining...

    val results = Future.traverse(contenders)(sample => {
      println(s"running ${sample.configID} at budget ${sample.budget}")
      f(sample.config, ri).map { evalResult =>
        Trial(
          sample.config,
          sample.configID,
          Some(evalResult),
          ri,
          sample.xElliptic
        )
      }
    })

    // returning recursively
    results.flatMap { results =>
      if (i >= iMax) {
        Future(samples ++ results)
      } else {
        runSucessiveHalving(f, results, iMax, n, eta, r, i + 1).map {
          nextResults =>
            results ++ nextResults
        }
      }
    }
  }
}
