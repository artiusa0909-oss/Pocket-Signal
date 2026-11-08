package com.pocketsignal.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

private val ScreenBg = Color(0xFF080D13)
private val PanelBg = Color(0xFF111B26)
private val Accent = Color(0xFF20D69A)
private val Muted = Color(0xFF91A0B1)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(8, 13, 19)
        window.navigationBarColor = android.graphics.Color.rgb(8, 13, 19)
        window.decorView.systemUiVisibility = 0
        setContent { PocketSignalCompanionScreen() }
    }
}

@androidx.compose.runtime.Composable
private fun PocketSignalCompanionScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val preferences = remember {
        context.getSharedPreferences(
            FloatingOverlayService.PREFS,
            android.content.Context.MODE_PRIVATE
        )
    }
    var gatewayUrl by remember {
        mutableStateOf(preferences.getString(FloatingOverlayService.KEY_GATEWAY, "").orEmpty())
    }
    var status by remember {
        mutableStateOf("Оригинальный Pocket Option не изменяется. Разреши показ поверх других приложений.")
    }
    var overlayAllowed by remember { mutableStateOf(Settings.canDrawOverlays(context)) }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                overlayAllowed = Settings.canDrawOverlays(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            background = ScreenBg,
            surface = PanelBg,
            primary = Accent
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = ScreenBg) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    "POCKET SIGNAL",
                    color = Accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Text(
                    "Плавающий AI-аналитик",
                    color = Color.White,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Отдельное окно поверх Pocket Option. Торговая программа запускается оригинальной, её APK, интерфейс и авторизация не затрагиваются.",
                    color = Muted,
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )

                Surface(color = PanelBg, shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("СОСТОЯНИЕ РАЗРЕШЕНИЙ", color = Color.White, fontWeight = FontWeight.Bold)
                        Text(
                            if (overlayAllowed) "✓ Отображение поверх приложений разрешено"
                            else "Нужно разрешить отображение поверх других приложений",
                            color = if (overlayAllowed) Accent else Color(0xFFFFC266),
                            fontSize = 13.sp
                        )
                        Button(
                            onClick = {
                                if (!overlayAllowed) {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                } else {
                                    val serviceIntent = Intent(context, FloatingOverlayService::class.java)
                                    ContextCompat.startForegroundService(context, serviceIntent)
                                    status = "Плавающее окно запущено. Теперь можно открыть Pocket Option."
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (overlayAllowed) "Включить плавающее окно" else "Разрешить плавающее окно")
                        }
                    }
                }

                Button(
                    onClick = {
                        if (!Settings.canDrawOverlays(context)) {
                            status = "Сначала разреши отображение поверх приложений."
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        } else {
                            ContextCompat.startForegroundService(
                                context,
                                Intent(context, FloatingOverlayService::class.java)
                            )
                            val launchIntent = context.packageManager.getLaunchIntentForPackage(POCKET_OPTION_PACKAGE)
                            if (launchIntent != null) {
                                context.startActivity(launchIntent)
                                status = "Pocket Option запущен. Значок PS появится поверх него."
                            } else {
                                status = "Pocket Option не найден. Установи оригинальное приложение с пакетом $POCKET_OPTION_PACKAGE."
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Запустить Pocket Option + окно PS")
                }

                OutlinedButton(
                    onClick = {
                        val launchIntent = context.packageManager.getLaunchIntentForPackage(POCKET_OPTION_PACKAGE)
                        if (launchIntent != null) context.startActivity(launchIntent)
                        else status = "Оригинальное приложение Pocket Option не обнаружено."
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Только открыть Pocket Option")
                }

                Surface(color = PanelBg, shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("ПОДКЛЮЧЕНИЕ GPT", color = Color.White, fontWeight = FontWeight.Bold)
                        Text(
                            "Укажи HTTPS-адрес собственного серверного шлюза. Он должен принимать POST /v1/chat с массивом messages и возвращать JSON с полем reply. Секретный ключ GPT хранится на сервере, не в APK.",
                            color = Muted,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                        OutlinedTextField(
                            value = gatewayUrl,
                            onValueChange = { gatewayUrl = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("HTTPS URL GPT gateway") },
                            placeholder = { Text("https://api.example.com") }
                        )
                        OutlinedButton(
                            onClick = {
                                val normalized = gatewayUrl.trim().trimEnd('/')
                                if (normalized.startsWith("https://") && normalized.length > 8) {
                                    preferences.edit()
                                        .putString(FloatingOverlayService.KEY_GATEWAY, normalized)
                                        .apply()
                                    status = "Адрес GPT-шлюза сохранён."
                                } else {
                                    status = "Нужен корректный HTTPS-адрес. HTTP не поддерживается."
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Сохранить адрес GPT")
                        }
                    }
                }

                Surface(color = PanelBg, shape = MaterialTheme.shapes.large) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("ВАЖНО", color = Color.White, fontWeight = FontWeight.Bold)
                        Text(
                            "Само плавающее окно не получает доступ к графику или закрытым данным Pocket Option. Для рыночного анализа отдельно подключается легальный проверенный источник котировок. GPT-чат заработает после настройки HTTPS-шлюза.",
                            color = Muted,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }

                Text(status, color = Accent, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

private const val POCKET_OPTION_PACKAGE = "com.potradeweb"
