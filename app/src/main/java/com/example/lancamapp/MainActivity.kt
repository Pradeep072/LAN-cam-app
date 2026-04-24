package com.example.lancamapp

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import com.example.lancamapp.database.CameraEntity // <--- Make sure this import is here
import com.example.lancamapp.ui.AddDeviceScreen
import com.example.lancamapp.ui.DashboardScreen
import com.example.lancamapp.ui.DeviceDetailScreen
import com.example.lancamapp.ui.ScannerScreen
import com.example.lancamapp.ui.theme.LancamappTheme

class MainActivity : ComponentActivity() {

    // Store data to pass between screens
    private var selectedIp: String = ""
    private var selectedType: String = ""
    private var selectedCamera: CameraEntity? = null // <--- FIXED: Added this missing variable

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

                when (currentScreen) {
                    Screen.DASHBOARD -> {
                        DashboardScreen(
                            onAddClick = { currentScreen = Screen.SCANNER },
                            onCameraClick = { camera ->
                                selectedCamera = camera // Save the clicked camera
                                currentScreen = Screen.DEVICE_DETAIL
                            }
                        )
                    }
                    Screen.SCANNER -> {
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
                        AddDeviceScreen(
                            scannedIp = selectedIp,
                            scannedType = selectedType,
                            onSaveComplete = { currentScreen = Screen.DASHBOARD },
                            onBack = { currentScreen = Screen.SCANNER }
                        )
                    }
                    Screen.DEVICE_DETAIL -> {
                        selectedCamera?.let { cam ->
                            DeviceDetailScreen(
                                camera = cam,
                                onBack = { currentScreen = Screen.DASHBOARD },
                                onPlaySelected = { urls ->
                                    // Log.d("MainActivity", "Selected URLs: $urls") // Optional
                                    val intent = Intent(this@MainActivity, MultiViewPlayerActivity::class.java)
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