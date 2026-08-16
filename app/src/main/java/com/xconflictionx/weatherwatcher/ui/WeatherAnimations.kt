package com.xconflictionx.weatherwatcher.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.*
import com.xconflictionx.weatherwatcher.R
import kotlin.random.Random

/**
 * Categorizes a NWS condition string into a stable visual category.
 */
fun getWeatherCategory(condition: String): String {
    val cond = condition.lowercase()
    return when {
        cond.contains("thunderstorm") -> "thunder"
        cond.contains("heavy rain") -> "heavy_rain"
        cond.contains("rain") || cond.contains("showers") -> "rain"
        cond.contains("drizzle") -> "drizzle"
        cond.contains("snow") || cond.contains("flurries") -> "snow"
        cond.contains("ice") || cond.contains("sleet") -> "ice"
        cond.contains("fog") || cond.contains("haze") || cond.contains("smoke") || cond.contains("mist") -> "mist"
        cond.contains("wind") -> "wind"
        // Move "Overcast/Cloudy" up so they take precedence over "Mostly"
        cond.contains("overcast") || cond == "cloudy" || cond.contains("broken clouds") -> "clouds_heavy"
        // Mixed logic: Only if it's not strictly clear or overcast
        cond.contains("partly") || cond.contains("scattered") || cond.contains("few clouds") || (cond.contains("mostly") && cond.contains("sunny")) -> "clouds_mixed"
        // Clear logic: "Clear", "Fair", "Mostly Clear" all count as Clear Sky
        cond.contains("sun") || cond.contains("clear") || cond.contains("fair") -> "clear"
        else -> "unknown"
    }
}

/**
 * Returns a list of Lottie resource IDs for a given category and time of day.
 */
fun getWeatherPool(category: String, isDay: Boolean): List<Int> {
    return when (category) {
        "thunder" -> if (isDay) {
            listOf(R.raw.weather_day_thunderstorm, R.raw.weather_thunder, R.raw.thunderstorms_day_extreme_rain)
        } else {
            listOf(R.raw.weather_night_thunderstorm, R.raw.weather_thunder)
        }
        
        "heavy_rain" -> if (isDay) {
            listOf(R.raw.weather_day_rain, R.raw.overcast_day_rain, R.raw.rain_background_animation)
        } else {
            listOf(R.raw.weather_night_rain, R.raw.rain_background_animation)
        }
        
        "rain" -> if (isDay) {
            listOf(R.raw.weather_icon_rain, R.raw.weather_day_rain, R.raw.weather_day_shower_rains)
        } else {
            listOf(R.raw.weather_icon_rain, R.raw.weather_night_rain, R.raw.weather_night_shower_rains)
        }
        
        "drizzle" -> if (isDay) {
            listOf(R.raw.partly_cloudy_day_drizzle, R.raw.weather_day_shower_rains)
        } else {
            listOf(R.raw.weather_night_shower_rains)
        }
        
        "snow" -> if (isDay) {
            listOf(R.raw.weather_day_snow, R.raw.overcast_day_snow, R.raw.snowing)
        } else {
            listOf(R.raw.weather_night_snow, R.raw.snowing)
        }
        
        "ice" -> listOf(R.raw.overcast_day_sleet)
        
        "mist" -> if (isDay) {
            listOf(R.raw.weather_day_mist, R.raw.overcast_day_fog)
        } else {
            listOf(R.raw.weather_night_mist, R.raw.overcast_day_fog)
        }
        
        "wind" -> listOf(R.raw.weather_windy)
        
        "clouds_heavy" -> if (isDay) {
            listOf(R.raw.overcast_day, R.raw.animated_cloud, R.raw.weather_day_broken_clouds)
        } else {
            listOf(R.raw.weather_night_broken_clouds, R.raw.animated_cloud)
        }
        
        "clouds_mixed" -> if (isDay) {
            listOf(R.raw.weather_partly_cloudy, R.raw.weather_icon_mostly_sunny, R.raw.weather_day_few_clouds, R.raw.weather_day_scattered_clouds)
        } else {
            listOf(R.raw.weather_partly_cloudy, R.raw.weather_night_few_clouds, R.raw.weather_night_scattered_clouds)
        }
        
        "clear" -> if (isDay) {
            listOf(R.raw.weather_sunny, R.raw.sunny_pure, R.raw.sunny_v1, R.raw.little_sun, R.raw.weather_day_clear_sky)
        } else {
            listOf(R.raw.its_sleeptime)
        }
        
        else -> emptyList()
    }
}

@Composable
fun WeatherBackground(resId: Int?, modifier: Modifier = Modifier) {
    // Crossfade provides a smooth transition when the resource ID changes
    Crossfade(
        targetState = resId,
        modifier = modifier, 
        animationSpec = tween(1500), 
        label = "weather_fade"
    ) { targetResId ->
        if (targetResId != null) {
            LottieWeatherAnimation(targetResId, Modifier.fillMaxSize())
        }
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
            .alpha(0.14f) // Reverted to 14% opacity
            .blur(radius = 7.dp), // Reverted to 7.dp blur
        contentScale = ContentScale.Crop,
        alignment = androidx.compose.ui.Alignment.Center
    )
}
