package net.harutiro.mattertest.chip

import android.content.Context
import chip.devicecontroller.ChipClusters
import chip.devicecontroller.ChipStructs
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object OnOffClient {
    private suspend fun onOffCluster(context: Context, nodeId: Long, endpoint: Int): ChipClusters.OnOffCluster {
        val devicePointer = ChipClient.getConnectedDevicePointer(context, nodeId)
        return ChipClusters.OnOffCluster(devicePointer, endpoint)
    }

    private fun commandCallback(continuation: CancellableContinuation<Unit>) =
        object : ChipClusters.DefaultClusterCallback {
            override fun onSuccess() = continuation.resume(Unit)
            override fun onError(error: Exception) = continuation.resumeWithException(error)
        }

    suspend fun on(context: Context, nodeId: Long, endpoint: Int) {
        val cluster = onOffCluster(context, nodeId, endpoint)
        suspendCancellableCoroutine { cluster.on(commandCallback(it)) }
    }

    suspend fun off(context: Context, nodeId: Long, endpoint: Int) {
        val cluster = onOffCluster(context, nodeId, endpoint)
        suspendCancellableCoroutine { cluster.off(commandCallback(it)) }
    }

    suspend fun toggle(context: Context, nodeId: Long, endpoint: Int) {
        val cluster = onOffCluster(context, nodeId, endpoint)
        suspendCancellableCoroutine { cluster.toggle(commandCallback(it)) }
    }

    suspend fun readOnOff(context: Context, nodeId: Long, endpoint: Int): Boolean {
        val cluster = onOffCluster(context, nodeId, endpoint)
        return suspendCancellableCoroutine { continuation ->
            cluster.readOnOffAttribute(object : ChipClusters.BooleanAttributeCallback {
                override fun onSuccess(value: Boolean) = continuation.resume(value)
                override fun onError(error: Exception) = continuation.resumeWithException(error)
            })
        }
    }
}
