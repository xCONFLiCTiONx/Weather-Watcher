package com.xconflictionx.weatherwatcher.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import com.airbnb.lottie.compose.*
import com.xconflictionx.weatherwatcher.R

@Composable
fun WeatherBackground(condition: String, modifier: Modifier = Modifier) {
    val cond = condition.lowercase()
    val resId = when {
        cond.contains("thunderstorm") -> R.raw.weather_thunder
        cond.contains("heavy rain") -> R.raw.rain_background_animation
        cond.contains("rain") || cond.contains("showers") -> R.raw.weather_icon_rain
        cond.contains("drizzle") -> R.raw.partly_cloudy_day_drizzle
        cond.contains("snow") || cond.contains("flurries") -> R.raw.overcast_day_snow
        cond.contains("ice") || cond.contains("sleet") -> R.raw.overcast_day_sleet
        cond.contains("fog") || cond.contains("haze") || cond.contains("smoke") -> R.raw.overcast_day_fog
        cond.contains("wind") -> R.raw.weather_windy
        cond.contains("overcast") -> R.raw.overcast_day
        cond.contains("cloud") -> R.raw.weather_partly_cloudy
        cond.contains("sun") || cond.contains("clear") -> R.raw.weather_icon_mostly_sunny
        else -> null
    }

    if (resId != null) {
        LottieWeatherAnimation(resId, modifier)
    }
}

@Composable
fun LottieWeatherAnimation(resId: Int, modifier: Modifier = Modifier) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(resId))
    val progress by animateLottieCompositionAsState(
        composition,
        iterations = LottieConstants.IterateForever
    )

    LottieAnimation(
        composition = composition,
        progress = { progress },
        modifier = modifier
            .alpha(0.10f), // Set to 15% opacity for a balanced subtle effect
        contentScale = ContentScale.Crop, // Filled to the edges without stretching (aspect ratio maintained)
        alignment = androidx.compose.ui.Alignment.Center
    )
}
