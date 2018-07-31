package com.zacharee1.boredsigns.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.support.v4.content.LocalBroadcastManager
import android.util.Log
import android.view.View
import android.widget.RemoteViews

import com.zacharee1.boredsigns.R
import com.zacharee1.boredsigns.activities.PermissionsActivity
import com.zacharee1.boredsigns.services.WeatherService
import com.zacharee1.boredsigns.util.Utils

class WeatherForecastWidget : AppWidgetProvider() {
    private var tempHigh: ArrayList<String>? = null
    private var tempLow: ArrayList<String>? = null
    private var icon: ArrayList<String>? = null
    private var times: ArrayList<String>? = null //保存所有获取到的时间
    private var weatherShowTime :Boolean = true
    private var showloading: Int? = 0

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val views = RemoteViews(context.packageName, R.layout.weather_forecast_widget)
        if (showloading==1){
            openLoading(views,appWidgetManager,appWidgetIds)
            showloading=0
            return
        }
        for (perm in PermissionsActivity.WEATHER_REQUEST) {
            if (context.checkCallingOrSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
                val intent = Intent(context, PermissionsActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
                intent.putExtra("class", this::class.java)
                context.startActivity(intent)
                return
            }
        }

        startService(context)

        val intent = Intent(context, WeatherService::class.java)
        intent.action = WeatherService.ACTION_UPDATE_WEATHER
        val pIntent = PendingIntent.getService(context, 10, intent, 0)
        views.setOnClickPendingIntent(R.id.main, pIntent)
        //views.setViewVisibility(R.id.loading, View.VISIBLE)
        //setYahooPendingIntent(views, context) 添加点击动作

        appWidgetManager.updateAppWidget(appWidgetIds, views) //fixBug:修改挂件内容后肯定是要刷新Widget的

        if (tempHigh != null && times != null){
            setThings(views, context)
            closeLoading(views,appWidgetManager,appWidgetIds)
        }else{
            openLoading(views,appWidgetManager,appWidgetIds)
            sendUpdate(context)
        }


    }

    private fun openLoading(views: RemoteViews,appWidgetManager: AppWidgetManager,  appWidgetIds: IntArray){
        views.setViewVisibility(R.id.loading, View.VISIBLE)
        appWidgetManager.updateAppWidget(appWidgetIds, views)

    }
    private fun closeLoading(views: RemoteViews,appWidgetManager: AppWidgetManager,  appWidgetIds: IntArray){
        views.setViewVisibility(R.id.loading, View.GONE)
        appWidgetManager.updateAppWidget(appWidgetIds, views)
    }


    override fun onReceive(context: Context?, intent: Intent?) {
        intent?.let {
            tempHigh = it.getStringArrayListExtra(WeatherService.EXTRA_TEMP)
            icon = it.getStringArrayListExtra(WeatherService.EXTRA_ICON)
            times = it.getStringArrayListExtra(WeatherService.EXTRA_TIME)
            tempLow = it.getStringArrayListExtra(WeatherService.EXTRA_TEMP_EX)
            weatherShowTime = it.getBooleanExtra(WeatherService.WEATHER_SHOW_TIME,true)
            showloading = it.getIntExtra(WeatherService.SHOW_LOADING,0)

        }

        super.onReceive(context, intent)
    }

    override fun onEnabled(context: Context?) {
        super.onEnabled(context)
        startService(context)
    }

    override fun onRestored(context: Context?, oldWidgetIds: IntArray?, newWidgetIds: IntArray?) {
        super.onRestored(context, oldWidgetIds, newWidgetIds)
        startService(context)
    }

    override fun onDeleted(context: Context?, appWidgetIds: IntArray?) {
        super.onDeleted(context, appWidgetIds)
        stopService(context)
    }

    override fun onDisabled(context: Context?) {
        super.onDisabled(context)
        stopService(context)
    }



    private fun setThings(views: RemoteViews, context: Context) {
        if ( tempHigh == null || times == null) {
            sendUpdate(context)
        } else {

            views.removeAllViews(R.id.weather_block_wrap)


            for (i in 0 until (tempLow?.size ?: 5)) {
                val view = RemoteViews(context.packageName, R.layout.weather_forecast_block)
                if (icon != null) view.setImageViewBitmap(R.id.icon, Utils.processBmp(icon?.get(i), context))
                view.setTextViewText(R.id.tempHigh, tempHigh?.get(i))

                if (weatherShowTime){
                    view.setViewVisibility(R.id.times,View.VISIBLE)
                    view.setTextViewText(R.id.times, times?.get(i))
                }else{
                    //view.setTextViewText(R.id.times, "")
                    view.setViewVisibility(R.id.times,View.GONE)
                }


                views.addView(R.id.weather_block_wrap, view)
            }


           // views.setTextViewText(R.id.dates, (times?.get(0) ?: "1/1") + "–" + (times?.get(times?.lastIndex ?: 0) ?: "1/5"))
        }
    }

    private fun sendUpdate(context: Context) {
        val intent = Intent(WeatherService.ACTION_UPDATE_WEATHER)
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
    }

    private fun startService(context: Context?) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context?.startForegroundService(Intent(context, WeatherService::class.java))
        } else {
            context?.startService(Intent(context, WeatherService::class.java))
        }
       // context?.startService(Intent(context, WeatherService::class.java))
    }

    private fun stopService(context: Context?) {

        context?.stopService(Intent(context, WeatherService::class.java))
    }
}

