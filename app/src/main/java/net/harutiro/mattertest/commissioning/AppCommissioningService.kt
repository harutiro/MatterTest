package net.harutiro.mattertest.commissioning

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.google.android.gms.home.matter.commissioning.CommissioningCompleteMetadata
import com.google.android.gms.home.matter.commissioning.CommissioningRequestMetadata
import com.google.android.gms.home.matter.commissioning.CommissioningService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.harutiro.mattertest.chip.ChipClient
import kotlin.random.Random

private const val TAG = "Matter"

class AppCommissioningService : Service(), CommissioningService.Callback {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var commissioningServiceDelegate: CommissioningService

    override fun onCreate() {
        super.onCreate()
        commissioningServiceDelegate = CommissioningService.Builder(this)
            .setCallback(this)
            .build()
    }

    override fun onBind(intent: Intent): IBinder = commissioningServiceDelegate.asBinder()

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    override fun onCommissioningRequested(metadata: CommissioningRequestMetadata) {
        val ipAddress = metadata.networkLocation.formattedIpAddress
        val port = metadata.networkLocation.port
        Log.d(TAG, "onCommissioningRequested ip=$ipAddress port=$port vendorId=${metadata.deviceDescriptor.vendorId}")

        scope.launch {
            val nodeId = Random.nextLong(1, Long.MAX_VALUE)
            try {
                ChipClient.establishPaseConnection(applicationContext, nodeId, ipAddress, port, metadata.passcode)
                Log.d(TAG, "PASE 成功 nodeId=$nodeId")
                ChipClient.commissionDevice(applicationContext, nodeId)
                Log.d(TAG, "自前ファブリックへのコミッショニング成功 nodeId=$nodeId")
                commissioningServiceDelegate.sendCommissioningComplete(
                    CommissioningCompleteMetadata.builder()
                        .setToken(nodeId.toString())
                        .build()
                )
            } catch (e: Exception) {
                Log.e(TAG, "自前ファブリックへのコミッショニング失敗", e)
                commissioningServiceDelegate.sendCommissioningError(CommissioningService.CommissioningError.OTHER)
            }
        }
    }
}