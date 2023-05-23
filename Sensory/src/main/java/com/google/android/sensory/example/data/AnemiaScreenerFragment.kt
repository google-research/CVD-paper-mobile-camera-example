package com.google.android.sensory.example.data

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.fhir.datacapture.QuestionnaireFragment
import com.google.android.sensory.R

/** A fragment class to show anemia questionnaire on screen. */
class AnemiaScreenerFragment: Fragment(R.layout.fragment_anemia_screening) {
  override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    setUpActionBar()
  }

  private fun setUpActionBar() {
    (requireActivity() as AppCompatActivity).supportActionBar?.apply {
      setDisplayHomeAsUpEnabled(true)
    }
  }
}