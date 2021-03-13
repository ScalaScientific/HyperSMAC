package com.scalasci.hypersmac.model

case class Trial[Config](config: Config,
                         configID: String,
                         cost: Option[Double] = None,
                         budget: Double,
                         xElliptic: Int,
                         note:Option[String] = None)
