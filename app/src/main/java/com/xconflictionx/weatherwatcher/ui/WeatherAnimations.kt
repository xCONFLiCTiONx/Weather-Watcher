package com.xconflictionx.weatherwatcher.ui

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

@Composable
fun WeatherBackground(condition: String, isDay: Boolean, modifier: Modifier = Modifier) {
    val cond = condition.lowercase()
    
    // session-stable randomization: Pick a random one for the category and stick to it.
    // Categorize into broad visual groups for randomization
    val category = when {
        cond.contains("thunderstorm") -> "thunder"
        cond.contains("heavy rain") -> "heavy_rain"
        cond.contains("rain") || cond.contains("showers") -> "rain"
        cond.contains("drizzle") -> "drizzle"
        cond.contains("snow") || cond.contains("flurries") -> "snow"
        cond.contains("ice") || cond.contains("sleet") -> "ice"
        cond.contains("fog") || cond.contains("haze") || cond.contains("smoke") || cond.contains("mist") -> "mist"
        cond.contains("wind") -> "wind"
        cond.contains("mostly") || cond.contains("partly") || cond.contains("scattered") || cond.contains("few clouds") || cond.contains("broken clouds") -> "clouds_mixed"
        cond.contains("overcast") || cond.contains("cloud") -> "clouds_heavy"
        cond.contains("sun") || cond.contains("clear") || cond.contains("fair") -> "clear"
        else -> "unknown"
    }

    // Build the specific resource pool based on category and time of day
    val resources = when (category) {
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
            listOf(R.raw.weather_day_rain, R.raw.weather_day_shower_rains, R.raw.weather_icon_rain)
        } else {
            listOf(R.raw.weather_night_rain, R.raw.weather_night_shower_rains, R.raw.weather_icon_rain)
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

    // Session-stable randomization: Pick one and remember it for the life of this composable
    val selectedResId = remember(category, isDay) {
        if (resources.isNotEmpty()) resources[Random.nextInt(resources.size)] else null
    }

    if (selectedResId != null) {
        LottieWeatherAnimation(selectedResId, modifier)
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
            .alpha(0.15f) // Subtle but visible
            .blur(radius = 12.dp),
        contentScale = ContentScale.Crop, // Filled to the edges without stretching (aspect ratio maintained)
        alignment = androidx.compose.ui.Alignment.Center
    )
}
