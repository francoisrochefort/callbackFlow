package com.example.lab7

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.lab7.ui.theme.Lab7Theme
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.collect
import javax.inject.Inject

@HiltAndroidApp
class Lab7App: Application()

@Module
@InstallIn(SingletonComponent::class)
class AppModule {

    @Provides
    fun provideUsbApi() : UsbApi = UsbApi()

    @Provides
    fun provideUsbRepository(usbApi: UsbApi) : UsbRepository = UsbRepository(usbApi)

}

sealed class Event {
    data class OnAngleChange(val angle: Int) : Event()
    data class OnBucketLoadChange(val bucketLoad: Int) : Event()
}

interface Callback {
    fun onEvent(event: Event)
    fun onError(error: Throwable)
}

// Implementation of an USB device API
class UsbApi {

    lateinit var callback: Callback

    @OptIn(DelicateCoroutinesApi::class)
    fun start(callback: Callback) {
        this.callback = callback
        GlobalScope.launch {
            try {
                var angle = 0
                while (true) {
                    callback.onEvent(Event.OnAngleChange(angle++))
                    delay(100L)
                }
            }
            catch (error: Throwable) {
                callback.onError(error)
            }
        }
    }
}

// Implementation of a USB device Repository
class UsbRepository @Inject constructor(
    private val usbApi: UsbApi
){
    @OptIn(ExperimentalCoroutinesApi::class)
    fun getEventFlow(): Flow<Event> = callbackFlow {

        val callback = object : Callback {
            override fun onEvent(event: Event) {
                when(event) {
                    is Event.OnAngleChange -> {
                        trySendBlocking(event)
                    }
                    else -> Unit
                }
            }
            override fun onError(error: Throwable) {
                cancel(CancellationException("UsbAdapter error"))
            }
        }
        usbApi.start(callback)
        awaitClose {
        }
    }
}

// Implementation of USB device ViewModel
@HiltViewModel
class MainViewModel @Inject constructor(
    private val repo: UsbRepository
)  : ViewModel() {

    var angle: Int by mutableStateOf(0)
    var bucketLoad: Int by mutableStateOf(0)

    init {
        collectEventFlow()
    }

    private fun collectEventFlow() = viewModelScope.launch {
        repo.getEventFlow().collect { event ->
            when (event) {
                is Event.OnAngleChange -> {
                    angle = event.angle
                }
                is Event.OnBucketLoadChange -> {
                    bucketLoad = event.bucketLoad
                }
            }
        }
    }
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Lab7Theme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen(viewModel: MainViewModel = hiltViewModel()) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = viewModel.angle.toString(),
            fontSize = 84.sp
        )
    }
}

