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

package com.google.android.sensing.hear.fragments

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.google.android.sensing.hear.R

data class CauseItem(val rank: Int, val cause: String, val likelihood: String)

class CausesListAdapter(private val context: Context) :
  ListAdapter<CauseItem, CausesListAdapter.CauseViewHolder>(DIFF_CALLBACK) {

  inner class CauseViewHolder(itemView: View) : ViewHolder(itemView) {
    val rankTextView: TextView = itemView.findViewById(R.id.rank)
    val causeTextView: TextView = itemView.findViewById(R.id.cause)
    val likelihoodTextView: TextView = itemView.findViewById(R.id.likelyhood)
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CauseViewHolder {
    val itemView = LayoutInflater.from(context).inflate(R.layout.item_insight, parent, false)
    return CauseViewHolder(itemView)
  }

  override fun onBindViewHolder(holder: CauseViewHolder, position: Int) {
    val currentItem = getItem(position)
    holder.rankTextView.text = currentItem.rank.toString()
    holder.causeTextView.text = currentItem.cause
    holder.likelihoodTextView.text = currentItem.likelihood
  }

  companion object {
    private val DIFF_CALLBACK =
      object : DiffUtil.ItemCallback<CauseItem>() {
        override fun areItemsTheSame(oldItem: CauseItem, newItem: CauseItem): Boolean {
          return oldItem.rank == newItem.rank
        }

        override fun areContentsTheSame(oldItem: CauseItem, newItem: CauseItem): Boolean {
          return oldItem == newItem
        }
      }
  }
}
