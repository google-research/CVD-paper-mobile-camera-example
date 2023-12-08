package com.google.android.sensing

import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

class OffsetDateTimeTypeAdapter : TypeAdapter<OffsetDateTime>() {
    override fun write(out: JsonWriter, value: OffsetDateTime) {
        out.value(DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(value))
    }

    override fun read(input: JsonReader): OffsetDateTime = OffsetDateTime.parse(input.nextString())
}