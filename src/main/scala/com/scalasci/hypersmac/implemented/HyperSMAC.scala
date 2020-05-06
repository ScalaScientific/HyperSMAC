package com.scalasci.hypersmac.implemented

import com.scalasci.hypersmac.api.RendersConfig
import com.scalasci.hypersmac.model.Trial

import scala.concurrent.{ExecutionContext, Future}

trait BudgetedSampleFunction[ConfigSample]
    extends ((ConfigSample, Double) => Future[Double])

trait HyperSMAC[ConfigSpace, ConfigSample] {

  def trainSurrogateModel(
    history: Seq[Trial[ConfigSample]]
  ): ConfigSample => Boolean

  def apply(space: ConfigSpace,
            f: BudgetedSampleFunction[ConfigSample],
            eta: Double = 3.0,
            R: Double = 20.0)(
    implicit renders: RendersConfig[ConfigSpace, ConfigSample],
    ec: ExecutionContext
  ): Future[Seq[Trial[ConfigSample]]] = {
    val sMax = math.floor(math.log(R) / math.log(eta)).toInt
    val b = (sMax + 1) * R

    def innerLoop(
      s: Int = sMax,
      tIn: Seq[Trial[ConfigSample]] = Seq.empty //these are included to condition smac.
    ): Future[Seq[Trial[ConfigSample]]] = {
      val r = R * math.pow(eta, -s)
      val n = math.ceil(b * math.pow(eta, s) / r).toInt
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
        val chooser = trainSurrogateModel(tIn)
        tInit.take(n) //.filter(sample => chooser(sample.config)).take(n)
      }

      val sh = runSucessiveHalving(f, t.toSeq, s, n, eta, r)
      val newResults = if (s >= 0) {
        sh.flatMap { results =>
          innerLoop(s - 1, tIn ++ results)
        }
      } else {
        sh
      }

      newResults.map(nr => tIn ++ nr)
    }
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
    val ni = n * math.pow(eta, -i)
    val ri = r * math.pow(eta, i)

    val contenders = samples
      .sortBy(_.cost.getOrElse(Double.MaxValue))
      .take(math.floor(ni / eta).toInt)

    val l = Future.traverse(contenders)(samp => {
      f(samp.config, ri).map { evalResult =>
        Trial(samp.config, samp.configID, Some(evalResult), ri, samp.xElliptic)
      }
    })

    l.flatMap { results =>
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
