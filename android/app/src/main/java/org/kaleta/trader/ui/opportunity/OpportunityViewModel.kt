package org.kaleta.trader.ui.opportunity

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class OpportunityViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "This is company Fragment"
    }
    val text: LiveData<String> = _text
}