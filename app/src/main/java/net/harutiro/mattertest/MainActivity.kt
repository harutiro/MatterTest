package net.harutiro.mattertest

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.home.matter.Matter
import com.google.android.gms.home.matter.commissioning.CommissioningRequest
import com.google.android.gms.home.matter.commissioning.CommissioningResult
import kotlinx.coroutines.launch
import net.harutiro.mattertest.chip.ChipClient
import net.harutiro.mattertest.chip.DescriptorClient
import net.harutiro.mattertest.chip.EndpointInfo
import net.harutiro.mattertest.chip.OnOffClient
import net.harutiro.mattertest.commissioning.AppCommissioningService
import net.harutiro.mattertest.data.AppDatabase
import net.harutiro.mattertest.data.DeviceEntity
import net.harutiro.mattertest.ui.theme.MatterTestTheme

private const val TAG = "Matter"
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val controller = ChipClient.getDeviceController(this)
        Log.d(TAG, "controllerNodeId=${controller.controllerNodeId} compressedFabricId=${controller.compressedFabricId} fabricIndex=${controller.fabricIndex}")

        setContent {
            MatterTestTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        padding = innerPadding
                    )
                }
            }
        }
    }
}

@Composable
fun MainScreen(
    padding: PaddingValues
){

    val context = LocalContext.current
    val activity = LocalActivity.current

    val dao = remember { AppDatabase.get(context).deviceDao() }
    val devices by dao.observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    val commissioningLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        Log.d(TAG, "resultCode=${result.resultCode}")
        if (result.resultCode != Activity.RESULT_OK) {
            Log.w(TAG, "キャンセルまたは失敗")
        }
        try {
            val commissioningResult =
                CommissioningResult.fromIntentSenderResult(result.resultCode, result.data)
            val descriptor = commissioningResult.commissionedDeviceDescriptor

            Log.d(TAG, "deviceName=${commissioningResult.deviceName}")
            Log.d(TAG, "vendorId=${descriptor?.vendorId} productId=${descriptor?.productId} deviceType=${descriptor?.deviceType}")
            Log.d(TAG, "token=${commissioningResult.token} room=${commissioningResult.room?.name}")

            val nodeId = commissioningResult.token?.toLongOrNull()
            if (nodeId != null) {
                scope.launch { dao.updateName(nodeId, commissioningResult.deviceName) }
            }
        } catch (e: ApiException) {
            Log.e(TAG, "結果の取り出しに失敗 statusCode=${e.statusCode}", e)
        }
    }

    fun startCommissioning() {
        val request = CommissioningRequest.builder()
            .setCommissioningService(ComponentName(context, AppCommissioningService::class.java))
            .build()
        Matter.getCommissioningClient(context)
            .commissionDevice(request)
            .addOnSuccessListener { intentSender ->
                commissioningLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            }
            .addOnFailureListener { e -> Log.e(TAG, "コミッショニングの開始に失敗", e) }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.d(TAG, "ACCESS_LOCAL_NETWORK granted=$granted")
        if (granted) startCommissioning()
    }

    Column(
        modifier = Modifier
            .padding(padding)
    ) {
        Button(
            onClick = {
                val granted = ContextCompat.checkSelfPermission(
                    context, Manifest.permission.ACCESS_LOCAL_NETWORK
                ) == PackageManager.PERMISSION_GRANTED
                if (granted) startCommissioning()
                else permissionLauncher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK)
            }
        ) {
            Text("Matter製品のQRの読み込み")
        }

        LazyColumn {
            items(devices, key = { it.nodeId }) { device ->
                DeviceRow(device)
            }
        }

    }
}

@Composable
fun DeviceRow(device: DeviceEntity) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("未調査") }
    var endpoints by remember { mutableStateOf<List<EndpointInfo>>(emptyList()) }

    val dao = remember { AppDatabase.get(context).deviceDao() }
    var unpairFailed by remember { mutableStateOf(false) }

    Column {
        Text("${device.name}  nodeId=${device.nodeId}")
        Text("状態: $status")
        Button(onClick = {
            scope.launch {
                status = "調査中…"
                try {
                    val all = DescriptorClient.discover(context, device.nodeId)
                    all.forEach { Log.d(TAG, "endpoint=${it.endpoint} label=${it.label} deviceTypes=${it.deviceTypes.map { t -> "0x%04X".format(t) }} clusters=${it.serverClusters.map { c -> "0x%04X".format(c) }}") }
                    endpoints = all.filter { it.hasOnOff }
                    status = "OnOff を持つエンドポイント: ${endpoints.map { it.endpoint }}"
                } catch (e: Exception) {
                    Log.e(TAG, "調査 失敗 nodeId=${device.nodeId}", e)
                    status = "調査 失敗"
                }
            }
        }) { Text("調査") }
        endpoints.forEach { info ->
            EndpointRow(nodeId = device.nodeId, info = info)
        }

        Button(onClick = {
            scope.launch {
                status = "削除中…"
                try {
                    ChipClient.unpairDevice(context, device.nodeId)     // ① デバイス側
                    dao.delete(device.nodeId)                           // ② アプリ側
                    Log.d(TAG, "ファブリックから外して、記録も消しました nodeId=${device.nodeId}")
                } catch (e: Exception) {
                    Log.e(TAG, "ファブリックからの削除に失敗 nodeId=${device.nodeId}", e)
                    status = "削除 失敗（デバイスにつながらない）"
                    unpairFailed = true
                }
            }
        }) { Text("削除") }

        if (unpairFailed) {
            Button(onClick = {
                scope.launch {
                    dao.delete(device.nodeId)                           // ② だけ
                    Log.w(TAG, "記録だけ消しました（デバイス側にはファブリックが残っている可能性）nodeId=${device.nodeId}")
                }
            }) { Text("記録だけ消す") }
        }
    }
}

@Composable
fun EndpointRow(nodeId: Long, info: EndpointInfo) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("未取得") }

    fun run(label: String, block: suspend () -> Unit) {
        scope.launch {
            status = "$label 中…"
            try {
                block()
            } catch (e: Exception) {
                Log.e(TAG, "$label 失敗 nodeId=$nodeId endpoint=${info.endpoint}", e)
                status = "$label 失敗"
            }
        }
    }

    Column {
        Text("  endpoint ${info.endpoint}: ${info.label ?: "(名前なし)"}  状態: $status")
        Row {
            Button(onClick = { run("On") { OnOffClient.on(context, nodeId, info.endpoint); status = "On" } }) { Text("On") }
            Button(onClick = { run("Off") { OnOffClient.off(context, nodeId, info.endpoint); status = "Off" } }) { Text("Off") }
            Button(onClick = { run("Toggle") { OnOffClient.toggle(context, nodeId, info.endpoint); status = "Toggle 済み" } }) { Text("Toggle") }
            Button(onClick = {
                run("読み取り") {
                    status = if (OnOffClient.readOnOff(context, nodeId, info.endpoint)) "On（読み取り）" else "Off（読み取り）"
                }
            }) { Text("読む") }
        }
    }
}
