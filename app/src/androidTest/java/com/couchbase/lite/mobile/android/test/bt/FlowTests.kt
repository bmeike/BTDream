package com.couchbase.lite.mobile.android.test.bt

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import org.junit.Test
import java.util.concurrent.Executors

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class FlowTests {
    val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    @Test
    fun mergeFlows() {
        val channel = Channel<String>()

        val flow1 = flow<String> {
            Log.d("#####", "#1: started")
            for (i in 1..100) {
                Log.d("#####", "#1: emit $i")
                emit("#1: $i")
                delay(1000)
            }
            Log.d("#####", "#1: finished")
        }

        val flow2 = flow<String> {
            Log.d("#####", "#2: started")
            for (i in 1..100) {
                Log.d("#####", "#2: emit $i")
                emit("#2: $i")
                delay(1000)
            }
            Log.d("#####", "#2: finished")
        }


        Log.d("#####", "start")

        val flow0 = flow {
            emit(channel.receive())
            awaitCancellation()
        }

        CoroutineScope(Dispatchers.Default).launch {
            flow0.collect {
                Log.d("#####", "From channel: $it")
            }
        }

        Log.d("#####", "create flow #1")
        CoroutineScope(dispatcher).launch { flow1.collect { channel.send(it) } }
        Log.d("#####", "create flow #2")
        CoroutineScope(dispatcher).launch { flow2.collect { channel.send(it) } }
        Log.d("#####", "finish")
    }
}
