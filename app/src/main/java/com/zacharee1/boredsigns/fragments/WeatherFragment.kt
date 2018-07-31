package com.zacharee1.boredsigns.fragments

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.LocationManager
import android.os.Bundle
import android.preference.EditTextPreference
import android.preference.Preference
import android.preference.PreferenceFragment
import android.preference.SwitchPreference
import com.zacharee1.boredsigns.R

import com.zacharee1.boredsigns.util.Utils
import com.zacharee1.boredsigns.widgets.WeatherForecastWidget
import com.zacharee1.boredsigns.widgets.WeatherWidget

class WeatherFragment : PreferenceFragment() {
    private val broadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            /*
            if (intent?.action == LocationManager.PROVIDERS_CHANGED_ACTION) {
                disableCurrentLocationIfNoLocationAvailable()
            }
            */
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.prefs_weather)

        val filter = IntentFilter(LocationManager.PROVIDERS_CHANGED_ACTION)
        context.registerReceiver(broadcastReceiver, filter)

        setSwitchStatesAndListeners()
    }

    override fun onDestroy() {
        super.onDestroy()
        context.unregisterReceiver(broadcastReceiver)
    }

    private fun setSwitchStatesAndListeners() {
        val celsius = findPreference("weather_unit") as SwitchPreference
        val useCurrent = findPreference("use_location") as SwitchPreference

        val weatherNum = findPreference("weather_num") as EditTextPreference
        val weatherShowTime = findPreference("weather_show_time") as SwitchPreference

        val ifEditLoc = findPreference("ifEditLoc") as SwitchPreference
        val editLoc = findPreference("editLoc") as EditTextPreference


        val listener = Preference.OnPreferenceChangeListener {
            pref, any ->
            val extras = Bundle()

            if (pref.key == "weather_unit") {
                extras.putBoolean("weather_unit", !(any as Boolean))
            }

            if (pref.key == "weather_show_time"){
                extras.putBoolean("weather_show_time", !(any as Boolean))
            }

            if (pref.key == "ifEditLoc"){
                extras.putBoolean("ifEditLoc", !(any as Boolean))
            }


            if (Utils.isWidgetInUse(WeatherWidget::class.java, context)) Utils.sendWidgetUpdate(context, WeatherWidget::class.java, extras)
            if (Utils.isWidgetInUse(WeatherForecastWidget::class.java, context)) Utils.sendWidgetUpdate(context, WeatherForecastWidget::class.java, extras)

            true
        }
        ifEditLoc.onPreferenceChangeListener = listener
        editLoc.onPreferenceChangeListener = listener
        weatherShowTime.onPreferenceChangeListener = listener
        weatherNum.onPreferenceChangeListener = listener
        celsius.onPreferenceChangeListener = listener
        useCurrent.onPreferenceChangeListener = listener

    }

}