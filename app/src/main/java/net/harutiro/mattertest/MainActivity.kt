package net.harutiro.mattertest

import android.app.Activity
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.home.matter.Matter
import com.google.android.gms.home.matter.commissioning.CommissioningRequest
import com.google.android.gms.home.matter.commissioning.CommissioningResult
import net.harutiro.mattertest.ui.theme.MatterTestTheme

private const val TAG = "Matter"
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
        } catch (e: ApiException) {
            Log.e(TAG, "結果の取り出しに失敗 statusCode=${e.statusCode}", e)
        }
    }

    Column(
        modifier = Modifier
            .padding(padding)
    ) {
        Button(
            onClick = {
                val request = CommissioningRequest.builder().build()
                Matter.getCommissioningClient(context)
                    .commissionDevice(request)
                    .addOnSuccessListener { intentSender ->
                        commissioningLauncher.launch(
                            IntentSenderRequest.Builder(intentSender).build()
                        )
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "コミッショニングの開始に失敗", e)
                    }
            }
        ) {
            Text("Matter製品のQRの読み込み")
        }

    }
}