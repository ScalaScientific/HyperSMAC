package com.scalasci.hypersmac.model

sealed trait HasSetup[Config] {
  val setup:TrialSetup[Config]
}

case class TrialSetup[Config](config: Config,
                         configID: String,
                         budget: Double,
                         xElliptic: Int,
                         note:Option[String] = None) extends HasSetup[Config] {
  override val setup: TrialSetup[Config] = this
}

case class TrialWithResult[Config](trial:TrialSetup[Config], cost:Double)extends HasSetup[Config] {
  override val setup: TrialSetup[Config] = trial
}