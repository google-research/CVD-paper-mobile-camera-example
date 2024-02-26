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

package com.google.android.sensing.capture

import androidx.annotation.GuardedBy

interface Shared<T> {
  fun acquire(): T // Return 'this' for convenience
  fun release() // Generic release mechanism
}

/**
 * Collectors should invoke acquire to get resource and invoke release when the resource use is
 * completed.
 */
class SharedCloseable<T : AutoCloseable>(private val resource: T) : Shared<T> {
  @GuardedBy("mLock") private var refCount = 0

  override fun acquire(): T {
    synchronized(this) {
      refCount++
      return this.resource
    }
  }

  override fun release() {
    synchronized(this) {
      refCount--
      if (refCount == 0) resource.close()
    }
  }
}

fun <T : AutoCloseable, R> SharedCloseable<T>.use(block: (T) -> R): R {
  var exception: Throwable? = null
  try {
    return block(this.acquire())
  } catch (e: Throwable) {
    exception = e
    throw e
  } finally {
    this.internalRelease(exception)
  }
}

internal fun <T : AutoCloseable> SharedCloseable<T>.internalRelease(cause: Throwable?) =
  when {
    cause == null -> release()
    else -> {
      try {
        release()
      } catch (releaseException: Throwable) {
        cause.addSuppressed(releaseException)
      }
    }
  }
