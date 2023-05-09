package io.tofhir.redcap.model

/**
 * A REDCap instrument.
 *
 * @param instrument_name  the name of instrument i.e. the unique identifier of instrument
 * @param instrument_label the label of instrument
 * */
final case class Instrument(instrument_name: String,
                            instrument_label: String)
