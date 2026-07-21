package com.example.ui

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.repository.ServiceRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ServiceViewModel(
    private val serviceRepository: ServiceRepository
) : ViewModel() {

    private val _isServiceRunning = MutableLiveData<Boolean>(false)
    val isServiceRunning: LiveData<Boolean> = _isServiceRunning

    init {
        viewModelScope.launch {
            serviceRepository.isServiceRunning.collectLatest { running ->
                _isServiceRunning.postValue(running)
            }
        }
    }

    fun startService() {
        serviceRepository.startService()
    }

    fun stopService() {
        serviceRepository.stopService()
    }

    fun restartService() {
        serviceRepository.restartService()
    }
}
