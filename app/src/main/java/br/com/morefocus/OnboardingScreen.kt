package br.com.morefocus

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private enum class OnboardingSlide(@DrawableRes val image: Int, @StringRes val title: Int, @StringRes val body: Int) {
    Limits(R.drawable.onboarding_limits, R.string.onboarding_limits_title, R.string.onboarding_limits_body),
    Focus(R.drawable.onboarding_focus, R.string.onboarding_focus_title, R.string.onboarding_focus_body),
    History(R.drawable.onboarding_history, R.string.onboarding_history_title, R.string.onboarding_history_body)
}

@Composable
internal fun OnboardingScreen(onFinish: () -> Unit) {
    val slides = OnboardingSlide.entries
    var index by rememberSaveable { mutableIntStateOf(0) }
    val slide = slides[index]
    val last = index == slides.lastIndex
    val title = stringResource(slide.title)
    val progress = stringResource(R.string.onboarding_progress, index + 1, slides.size)
    val previousSlide = { index = slide.ordinal - 1 }
    BackHandler(enabled = index > 0, onBack = previousSlide)

    Scaffold { insets ->
        Column(
            Modifier.fillMaxSize().padding(insets).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                Modifier.widthIn(max = 440.dp).fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (index > 0) {
                    TextButton(onClick = previousSlide) { Text(stringResource(R.string.onboarding_back)) }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onFinish) { Text(stringResource(R.string.onboarding_skip)) }
            }
            key(slide) {
                Column(
                    Modifier.weight(1f).widthIn(max = 440.dp).fillMaxWidth()
                        .verticalScroll(rememberScrollState()).padding(vertical = 16.dp)
                        .semantics { paneTitle = title },
                    verticalArrangement = Arrangement.Center
                ) {
                    Image(
                        painterResource(slide.image), contentDescription = null,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1.5f).clip(RoundedCornerShape(24.dp))
                    )
                    Row(
                        Modifier.padding(top = 24.dp).semantics { contentDescription = progress },
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        slides.indices.forEach { position ->
                            val active = position == index
                            Spacer(Modifier.size(if (active) 24.dp else 8.dp, 6.dp).background(
                                if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                CircleShape
                            ))
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(title, modifier = Modifier.semantics { heading() },
                        style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(slide.body), style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Button(
                onClick = { if (last) onFinish() else index = slide.ordinal + 1 },
                modifier = Modifier.widthIn(max = 440.dp).fillMaxWidth().padding(vertical = 16.dp).heightIn(min = 52.dp)
            ) {
                Text(stringResource(if (last) R.string.onboarding_start else R.string.onboarding_next))
            }
        }
    }
}
