package com.scalasci.hypersmac.implemented

import com.scalasci.hypersmac.api.{Optimizer, RendersAndSamplesConfig}
import com.scalasci.hypersmac.model.{HasSetup, TrialSetup, TrialWithResult}

import scala.concurrent.{ExecutionContext, Future}

trait SimpleCostFunction[ConfigSample] {
  type Cost = Double

  def evaluate(configuration: ConfigSample, budget: Double)(implicit executionContext: ExecutionContext): Future[Cost]
}

trait BudgetedSampleFunction[ConfigSample] {
  type Cost = Double

  def evaluate(configurations: Seq[TrialSetup[ConfigSample]],
               budget: Double)(implicit executionContext: ExecutionContext):
  Future[Seq[TrialWithResult[ConfigSample]]]
}

object BudgetedSampleFunction {
  def fromSimpleCostFunction[ConfigSample](f: SimpleCostFunction[ConfigSample]):
  BudgetedSampleFunction[ConfigSample] = {
    new BudgetedSampleFunction[ConfigSample] {
      override def evaluate(configurations: Seq[TrialSetup[ConfigSample]],
                            budget: Double)(implicit executionContext: ExecutionContext):
      Future[Seq[TrialWithResult[ConfigSample]]] = {
        Future.sequence(configurations.map { cfg => f.evaluate(cfg.config, budget)
          .map { cost => TrialWithResult(cfg, cost) } })
      }
    }
  }
}


trait HyperSMAC[ConfigSpace, ConfigSample] {

  // implement this for various surrogate model families, like GMM or decision tree/XGB
  // typically, you will want RendersConfigSample[ConfigSample] so you can train the surrogate model on the rendered
  // sampled configs. The surrogate model should be in the form of a function that returns true for "good"
  // configurations.
  def trainSurrogateModel(
                           history: Seq[TrialWithResult[ConfigSample]]
                         ): ConfigSample => Boolean

  /**
    * Run HyperSmac
    *
    * @param space   The parameter space from which to sample configurations
    * @param f       the loss function. parameters will be optimized to minimize this value
    * @param eta     the hyperband eta parameter to use on inner runs
    * @param R       the hyperband R parameter
    * @param runs    number of inner hyperband runs. setting this greater than one will allow smac optimization
    * @param history a resume history, empty if initializing.
    * @param renders means of converting a sample from the parameter space into a double array, a smac feature vector
    * @param ec      an execution context
    * @return all of the completed trials. feeding this back into the function will resume optimization.
    */
  def apply(eta: Double = 3.0,
            R: Double = 50.0,
            runs: Int = 1,
            history: Seq[TrialWithResult[ConfigSample]] = Seq.empty)(
             implicit renders: RendersAndSamplesConfig[ConfigSpace, ConfigSample],
             ec: ExecutionContext
           ): Optimizer[ConfigSpace, ConfigSample] = (space: ConfigSpace, f: BudgetedSampleFunction[ConfigSample]) => {
    //calculate HyperBand constants
    val sMax = math.floor(math.log(R) / math.log(eta)).toInt
    val b = (sMax + 1) * R

    // successive calls to inner loop will condition space sample on previous good samples smac filter.
    def innerLoop(
                   s: Int = sMax,
                   historyInner: Seq[TrialWithResult[ConfigSample]] = history, //these are included to condition smac.
                   remainingRuns: Int = runs
                 ): Future[Seq[TrialWithResult[ConfigSample]]] = {
      val r = R * math.pow(eta, -s) // largest resources at smallest s
      val n = math.ceil(b * math.pow(eta, s) / r).toInt

      val t = {
        // chooser is a binary classifier. Typically, the surrogate model will be attempting to learn which
        // configurations give good scores.
        val chooser = trainSurrogateModel(historyInner)
        // randomly sample new configurations
        val tInit: Iterator[HasSetup[ConfigSample]] =
          Iterator
            .from(historyInner.map(_.trial.xElliptic).:+(-1).max + 1) //zero if no history
            .map(xElliptic => xElliptic -> renders.sample(space, xElliptic))
            .map {
              case (xElliptic, initial) =>
                TrialSetup(
                  initial,
                  java.util.UUID.randomUUID().toString,
                  0.0,
                  xElliptic
                )
            }
        // filter them per their predicted quality according to chooser.
        tInit.filter(sample => chooser(sample.setup.config)).take(n)
      }

      // run successive halving on the
      val sh = runSuccessiveHalving(f, t.toSeq, s, n, eta, r)
      val newResults = if (s >= 0) {
        sh.flatMap { results =>
          innerLoop(s - 1, historyInner ++ results, runs)
        }
      } else if (remainingRuns < 0) {
        sh.flatMap { results =>
          innerLoop(sMax, historyInner ++ results, runs - 1)
        }
      } else {
        // we are done. don't recurse any deeper.
        Future(historyInner)
      }

      newResults
    }

    // recursively runs the optimization for the specified number of runs.
    innerLoop()
  }

  private def runSuccessiveHalving(
                                   f: BudgetedSampleFunction[ConfigSample],
                                   samples: Seq[HasSetup[ConfigSample]],
                                   iMax: Int,
                                   n: Int,
                                   eta: Double,
                                   r: Double,
                                   i: Int = 0
                                 )(implicit ec: ExecutionContext): Future[Seq[TrialWithResult[ConfigSample]]] = {

    // number of sub-runs for this iteration
    val ni = n * math.pow(eta, -i)

    //  resource allotment for each sub-run
    val ri = r * math.pow(eta, i)

    val contenders =
      samples // this might resume a low-budget run from history if it's good.
        .filter {
          _.setup.budget <= ri
        } // ...as long as this SH budget is higher than the candidate's max.
        .sortBy { case cfg: TrialSetup[ConfigSample] => Double.MaxValue
        case cfg: TrialWithResult[ConfigSample] => cfg.cost
        }
        .take(math.floor(ni / eta).toInt) // we will resume the "ni" most promising-looking candidates remaining...

    val results = f.evaluate(contenders.map(_.setup.copy(budget = ri)), ri)

    // returning recursively
    results.flatMap { results =>
      if (i >= iMax) {
        Future{
          samples.flatMap {
            //Only return proposals which have been scored.
            case cfg: TrialWithResult[ConfigSample] => Some(cfg)
            case _: TrialSetup[ConfigSample] => None
          } ++ results  // include the new results
        }
      } else {
        runSuccessiveHalving(f, results, iMax, n, eta, r, i + 1).map {
          nextResults =>
            results ++ nextResults
        }
      }
    }
  }
}
