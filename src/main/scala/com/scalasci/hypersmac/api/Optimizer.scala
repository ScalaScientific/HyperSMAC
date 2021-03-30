package com.scalasci.hypersmac.api

import com.scalasci.hypersmac.implemented.BudgetedSampleFunction
import com.scalasci.hypersmac.model.TrialWithResult

import scala.concurrent.Future

trait Optimizer [ConfigSpace, ConfigSample]{
  def produceTrials(space: ConfigSpace,
                    f: BudgetedSampleFunction[ConfigSample],
                    note:Option[String]): Future[Seq[TrialWithResult[ConfigSample]]]
}
