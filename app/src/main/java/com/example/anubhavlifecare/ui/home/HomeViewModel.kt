package com.example.anubhavlifecare.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.anubhavlifecare.data.model.Test
import com.example.anubhavlifecare.data.repository.TestRepository
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    private val testRepository = TestRepository()

    private val _welcomeMessage = MutableLiveData<String>().apply {
        value = "AKTIV Admin"
    }
    val welcomeMessage: LiveData<String> = _welcomeMessage

    private val _popularTests = MutableLiveData<List<Test>>()
    val popularTests: LiveData<List<Test>> = _popularTests

    private val _isLoading = MutableLiveData<Boolean>()
    val isLoading: LiveData<Boolean> = _isLoading

    init {
        loadPopularTests()
    }

    private fun loadPopularTests() {
        viewModelScope.launch {
            _isLoading.value = true
            testRepository.getPopularTests().fold(
                onSuccess = { tests ->
                    _popularTests.value = tests
                },
                onFailure = {
                    // Handle error
                    _popularTests.value = emptyList()
                }
            )
            _isLoading.value = false
        }
    }
}