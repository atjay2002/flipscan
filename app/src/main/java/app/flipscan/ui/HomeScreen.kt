package app.flipscan.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(onDoubleSide: () -> Unit, onRegular: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("FlipScan", style = MaterialTheme.typography.headlineLarge)
        Text("Both sides of a card on one page. Offline, no ads.", modifier = Modifier.padding(bottom = 20.dp))
        BigButton("Double Side", "Aadhaar, PAN, DL, voter ID, college ID", onDoubleSide)
        Spacer(Modifier.height(12.dp))
        BigButton("Regular", "Passbooks, certificates, any document", onRegular)
    }
}

@Composable
private fun BigButton(title: String, subtitle: String, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth().height(104.dp), shape = RoundedCornerShape(20.dp)) {
        Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
