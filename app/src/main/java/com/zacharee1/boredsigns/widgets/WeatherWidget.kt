package com.zacharee1.boredsigns.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

import android.os.Handler
import android.support.v4.content.LocalBroadcastManager
import android.view.View
import android.widget.RemoteViews

import com.zacharee1.boredsigns.R
import com.zacharee1.boredsigns.activities.PermissionsActivity
import com.zacharee1.boredsigns.services.WeatherService
import com.zacharee1.boredsigns.util.Utils
import android.support.v4.content.ContextCompat



class  WeatherWidget : AppWidgetProvider() {
    private var temp: String? = null
    private var desc: String? = null
    private var icon: String? = null
    private var loc: String? = null
    private var ifEditloc: Boolean = false
    private var editLoc: String? = null
    private var showloading: Int? = 0

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val views = RemoteViews(context.packageName, R.layout.weather_widget)
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

        //appWidgetManager.updateAppWidget(appWidgetIds, views) //fixBug:修改挂件内容后肯定是要刷新Widget的

        //val handler = Handler()

        if (temp != null && desc != null){
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
        val handler = Handler()
        handler.postDelayed({
            closeLoading(views, appWidgetManager, appWidgetIds)
        },5000) //错误处理：5秒后都要去掉


    }
    private fun closeLoading(views: RemoteViews,appWidgetManager: AppWidgetManager,  appWidgetIds: IntArray){
        views.setViewVisibility(R.id.loading, View.GONE)
        appWidgetManager.updateAppWidget(appWidgetIds, views)
    }


    override fun onReceive(context: Context, intent: Intent?) {
        intent?.let {
            temp = it.getStringExtra(WeatherService.EXTRA_TEMP)
            desc = it.getStringExtra(WeatherService.EXTRA_DESC)
            icon = it.getStringExtra(WeatherService.EXTRA_ICON)
            loc = it.getStringExtra(WeatherService.EXTRA_LOC)
            showloading = it.getIntExtra(WeatherService.SHOW_LOADING,0)
            ifEditloc = it.getBooleanExtra(WeatherService.IF_EDIT_LOC,false)
            editLoc = it.getStringExtra(WeatherService.EDIT_LOC)
        }


        super.onReceive(context, intent)
    }

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        startService(context)
    }

    override fun onRestored(context: Context, oldWidgetIds: IntArray?, newWidgetIds: IntArray?) {
        super.onRestored(context, oldWidgetIds, newWidgetIds)
        startService(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray?) {
        super.onDeleted(context, appWidgetIds)
        stopService(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        stopService(context)
    }
/* 添加点击动作
    private fun setYahooPendingIntent(views: RemoteViews, context: Context) {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse("https://openweathermap.org/")
        val pendingIntent = PendingIntent.getActivity(context, 1337, intent, 0)

        views.setOnClickPendingIntent(R.id.owm, pendingIntent)
    }
    */


    private fun setThings(views: RemoteViews, context: Context) {
        if (desc == null || temp == null ) {
            sendUpdate(context)
        }
        else {
            views.setImageViewBitmap(R.id.icon, Utils.processBmp(icon, context))
            views.setTextViewText(R.id.title, desc)
            views.setTextViewText(R.id.temp, temp)

            if (ifEditloc)loc = editLoc
            views.setTextViewText(R.id.loc,loc)

        }
    }


    private fun sendUpdate(context: Context) {
        val intent = Intent(WeatherService.ACTION_UPDATE_WEATHER)
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
    }

    private fun startService(context: Context) {
        ContextCompat.startForegroundService(context, Intent(context, WeatherService::class.java))
    }

    private fun stopService(context: Context) {
        context.stopService(Intent(context, WeatherService::class.java))
    }
}

