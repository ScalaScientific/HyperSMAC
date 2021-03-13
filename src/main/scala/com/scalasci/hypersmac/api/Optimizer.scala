package com.scalasci.hypersmac.api

import com.scalasci.hypersmac.implemented.BudgetedSampleFunction
import com.scalasci.hypersmac.model.Trial

import scala.concurrent.Future

trait Optimizer [ConfigSpace, ConfigSample]{
  def apply(space: ConfigSpace,
                                       f: BudgetedSampleFunction[ConfigSample]): Future[Seq[Trial[ConfigSample]]]
}
