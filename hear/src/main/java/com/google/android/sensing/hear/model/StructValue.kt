/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.sensing.hear.model

data class StructValue(val fields: List<Field>)

data class Field(val key: String, val value: Value)

data class Value(val numberValue: Double)

fun parseStructValue(input: String): StructValue {
  val fields = mutableListOf<Field>()

  // Very basic parsing assuming the input format does not change.
  // This is not a robust solution for variable input or different struct_value formats.
  val lines = input.split("\n")
  var key = ""
  var value = 0.0

  lines.forEach { line ->
    when {
      line.contains("key:") -> {
        key = line.split("\"")[1]
      }
      line.contains("number_value:") -> {
        value = line.split(":")[1].trim().toDouble()
        if (key != "lri" && key != "uri") fields.add(Field(key, Value(value)))
      }
    }
  }

  return StructValue(fields)
}
