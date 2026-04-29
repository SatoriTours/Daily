package com.dailysatori

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.dailysatori.core.navigation.DailySatoriNavHost

@Composable
fun DailySatoriApp() {
    val navController = rememberNavController()
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        DailySatoriNavHost(navController)
    }
}
