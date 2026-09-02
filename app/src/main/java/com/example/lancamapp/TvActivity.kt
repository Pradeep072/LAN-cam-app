package com.example.lancamapp

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.example.lancamapp.database.CameraEntity
import com.example.lancamapp.ui.AddDeviceScreen
import com.example.lancamapp.ui.DashboardScreen
import com.example.lancamapp.ui.DeviceDetailScreen
import com.example.lancamapp.ui.ScannerScreen
import com.example.lancamapp.ui.theme.LancamappTheme

class TvActivity : ComponentActivity() {

    private var selectedIp: String = ""
    private var selectedType: String = ""
    private var selectedCamera: CameraEntity? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { /* Handle results if needed */ }

        permissionLauncher.launch(arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE
        ))

        setContent {
            LancamappTheme {
                var currentScreen by remember { mutableStateOf(Screen.DASHBOARD) }
                var backPressedTime by remember { mutableLongStateOf(0L) }
                val context = LocalContext.current

                when (currentScreen) {
                    Screen.DASHBOARD -> {
                        BackHandler {
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - backPressedTime < 2000) {
                                (context as? ComponentActivity)?.finish()
                            } else {
                                backPressedTime = currentTime
                                Toast.makeText(context, "Press back again to exit", Toast.LENGTH_SHORT).show()
                            }
                        }

                        DashboardScreen(
                            onAddClick = { currentScreen = Screen.SCANNER },
                            onCameraClick = { camera ->
                                selectedCamera = camera
                                currentScreen = Screen.DEVICE_DETAIL
                            }
                        )
                    }
                    Screen.SCANNER -> {
                        BackHandler {
                            currentScreen = Screen.DASHBOARD
                        }

                        ScannerScreen(
                            onDeviceSelected = { ip, type ->
                                selectedIp = ip
                                selectedType = type
                                currentScreen = Screen.ADD_DEVICE
                            },
                            onBack = { currentScreen = Screen.DASHBOARD }
                        )
                    }
                    Screen.ADD_DEVICE -> {
                        BackHandler {
                            currentScreen = Screen.SCANNER
                        }

                        AddDeviceScreen(
                            scannedIp = selectedIp,
                            scannedType = selectedType,
                            onSaveComplete = { currentScreen = Screen.DASHBOARD },
                            onBack = { currentScreen = Screen.SCANNER }
                        )
                    }
                    Screen.DEVICE_DETAIL -> {
                        BackHandler {
                            currentScreen = Screen.DASHBOARD
                        }

                        selectedCamera?.let { cam ->
                            DeviceDetailScreen(
                                camera = cam,
                                onBack = { currentScreen = Screen.DASHBOARD },
                                onPlaySelected = { urls ->
                                    val intent = Intent(this@TvActivity, MultiViewPlayerActivity::class.java)
                                    intent.putStringArrayListExtra("URL_LIST", ArrayList(urls))
                                    startActivity(intent)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}