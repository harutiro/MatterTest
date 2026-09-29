package net.harutiro.mattertest.chip


import android.content.Context
import chip.devicecontroller.ChipClusters
import chip.devicecontroller.ChipStructs
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

data class EndpointInfo(
    val endpoint: Int,
    val deviceTypes: List<Long>,
    val serverClusters: List<Long>,
    val label: String?,
) {
    val hasOnOff: Boolean get() = ONOFF_CLUSTER_ID in serverClusters

    companion object {
        const val ONOFF_CLUSTER_ID = 0x0006L
        const val BRIDGED_INFO_CLUSTER_ID = 0x0039L
    }
}

object DescriptorClient {

    private suspend fun readPartsList(devicePointer: Long): List<Int> =
        suspendCancellableCoroutine { continuation ->
            ChipClusters.DescriptorCluster(devicePointer, 0).readPartsListAttribute(
                object : ChipClusters.DescriptorCluster.PartsListAttributeCallback {
                    override fun onSuccess(value: List<Int>) = continuation.resume(value)
                    override fun onError(error: Exception) = continuation.resumeWithException(error)
                }
            )
        }

    private suspend fun readDeviceTypes(devicePointer: Long, endpoint: Int): List<Long> =
        suspendCancellableCoroutine { continuation ->
            ChipClusters.DescriptorCluster(devicePointer, endpoint).readDeviceTypeListAttribute(
                object : ChipClusters.DescriptorCluster.DeviceTypeListAttributeCallback {
                    override fun onSuccess(value: List<ChipStructs.DescriptorClusterDeviceTypeStruct>) =
                        continuation.resume(value.map { it.deviceType })
                    override fun onError(error: Exception) = continuation.resumeWithException(error)
                }
            )
        }

    private suspend fun readServerList(devicePointer: Long, endpoint: Int): List<Long> =
        suspendCancellableCoroutine { continuation ->
            ChipClusters.DescriptorCluster(devicePointer, endpoint).readServerListAttribute(
                object : ChipClusters.DescriptorCluster.ServerListAttributeCallback {
                    override fun onSuccess(value: List<Long>) = continuation.resume(value)
                    override fun onError(error: Exception) = continuation.resumeWithException(error)
                }
            )
        }

    private suspend fun readBridgedLabel(devicePointer: Long, endpoint: Int): String =
        suspendCancellableCoroutine { continuation ->
            ChipClusters.BridgedDeviceBasicInformationCluster(devicePointer, endpoint).readNodeLabelAttribute(
                object : ChipClusters.CharStringAttributeCallback {
                    override fun onSuccess(value: String) = continuation.resume(value)
                    override fun onError(error: Exception) = continuation.resumeWithException(error)
                }
            )
        }

    suspend fun discover(context: Context, nodeId: Long): List<EndpointInfo> {
        val devicePointer = ChipClient.getConnectedDevicePointer(context, nodeId)
        val endpoints = listOf(0) + readPartsList(devicePointer)
        return endpoints.map { endpoint ->
            val clusters = readServerList(devicePointer, endpoint)
            EndpointInfo(
                endpoint = endpoint,
                deviceTypes = readDeviceTypes(devicePointer, endpoint),
                serverClusters = clusters,
                label = if (EndpointInfo.BRIDGED_INFO_CLUSTER_ID in clusters) {
                    runCatching { readBridgedLabel(devicePointer, endpoint) }.getOrNull()
                } else null,
            )
        }
    }
}
