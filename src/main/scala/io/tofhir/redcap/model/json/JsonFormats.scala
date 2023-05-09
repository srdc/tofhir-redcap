package io.tofhir.redcap.model.json

import org.json4s.jackson.Serialization
import org.json4s.{Formats, NoTypeHints}

object JsonFormats {
  def getFormats: Formats = Serialization.formats(NoTypeHints)
}
