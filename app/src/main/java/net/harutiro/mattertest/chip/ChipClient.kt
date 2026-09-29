package net.harutiro.mattertest.chip

import android.content.Context
import chip.devicecontroller.ChipDeviceController
import chip.devicecontroller.CommissionParameters
import chip.devicecontroller.ControllerParams
import chip.platform.AndroidBleManager
import chip.platform.AndroidChipPlatform
import chip.platform.AndroidNfcCommissioningManager
import chip.platform.ChipMdnsCallbackImpl
import chip.platform.DiagnosticDataProviderImpl
import chip.platform.NsdManagerServiceBrowser
import chip.platform.NsdManagerServiceResolver
import chip.platform.PreferencesConfigurationManager
import chip.platform.PreferencesKeyValueStoreManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


object ChipClient {
    private const val VENDOR_ID = 0xFFF4   // テスト用のベンダー ID（本番では CSA から取得した ID）

    private var platform: AndroidChipPlatform? = null
    private var controller: ChipDeviceController? = null

    @Synchronized
    fun getDeviceController(context: Context): ChipDeviceController {
        controller?.let { return it }                 // 作成済みならそれを返す

        val appContext = context.applicationContext   // Activity ではなくアプリの Context を使う
        ChipDeviceController.loadJni()                // ① so を読み込む

        if (platform == null) {                       // ② Android の部品を渡す
            platform = AndroidChipPlatform(
                AndroidBleManager(appContext),
                AndroidNfcCommissioningManager(),
                PreferencesKeyValueStoreManager(appContext),
                PreferencesConfigurationManager(appContext),
                NsdManagerServiceResolver(
                    appContext,
                    NsdManagerServiceResolver.NsdManagerResolverAvailState()
                ),
                NsdManagerServiceBrowser(appContext),
                ChipMdnsCallbackImpl(),
                DiagnosticDataProviderImpl(appContext),
            )
        }

        return ChipDeviceController(                   // ③ 自前ファブリックの管理者を作る
            ControllerParams.newBuilder()
                .setControllerVendorId(VENDOR_ID)
                .build()
        ).also { controller = it }
    }

    suspend fun establishPaseConnection(
        context: Context, nodeId: Long, ipAddress: String, port: Int, passcode: Long
    ) {
        val controller = getDeviceController(context)
        suspendCancellableCoroutine { continuation ->
            controller.setCompletionListener(object : BaseCompletionListener() {
                override fun onPairingComplete(errorCode: Long) {
                    if (errorCode == 0L) continuation.resume(Unit)
                    else continuation.resumeWithException(IllegalStateException("PASE 失敗 errorCode=$errorCode"))
                }
                override fun onError(error: Throwable?) {
                    continuation.resumeWithException(error ?: IllegalStateException("PASE 失敗"))
                }
            })
            controller.establishPaseConnection(nodeId, ipAddress, port, passcode)
        }
    }

    suspend fun commissionDevice(context: Context, nodeId: Long) {
        val controller = getDeviceController(context)
        suspendCancellableCoroutine { continuation ->
            controller.setCompletionListener(object : BaseCompletionListener() {
                override fun onCommissioningComplete(nodeId: Long, errorCode: Long) {
                    if (errorCode == 0L) continuation.resume(Unit)
                    else continuation.resumeWithException(IllegalStateException("コミッショニング失敗 errorCode=$errorCode"))
                }
                override fun onError(error: Throwable?) {
                    continuation.resumeWithException(error ?: IllegalStateException("コミッ��ョニング失敗"))
                }
            })
            controller.commissionDevice(nodeId, CommissionParameters.Builder().build())
        }
    }
}