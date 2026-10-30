package com.workhub.app.ui.theme

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.workhub.app.R

/** The existing ascent symbol and DEFY wordmark share the current theme's foreground. */
@Composable
fun DefyBrand(modifier: Modifier = Modifier, showChinese: Boolean = true) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(painterResource(R.drawable.ic_brand_mark), null, Modifier.size(30.dp), tint = MaterialTheme.colorScheme.onSurface)
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(painterResource(R.drawable.ic_defy_wordmark), stringResource(R.string.brand_english), Modifier.width(77.dp).height(22.dp), tint = MaterialTheme.colorScheme.onSurface)
            if (showChinese) Text("跃过引力", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
