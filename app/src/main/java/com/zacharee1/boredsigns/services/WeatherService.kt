package com.zacharee1.boredsigns.services

import android.Manifest
//import android.annotation.RequiresPermission
import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.*
import android.content.pm.PackageManager
//import android.location.Geocoder
//import android.location.Location
//import android.location.LocationListener
//import android.location.LocationManager
import android.location.LocationManager.NETWORK_PROVIDER
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.preference.PreferenceManager
import android.support.annotation.RequiresPermission
import android.support.v4.content.LocalBroadcastManager
import android.widget.Toast

import com.baidu.location.BDAbstractLocationListener
import com.baidu.location.BDLocation
import com.baidu.location.LocationClient
import com.baidu.location.LocationClientOption
import com.baidu.location.LocationClientOption.LocationMode

import com.zacharee1.boredsigns.R
import com.zacharee1.boredsigns.util.Utils
import com.zacharee1.boredsigns.widgets.WeatherForecastWidget
import com.zacharee1.boredsigns.widgets.WeatherWidget
import github.vatsal.easyweather.Helper.TempUnitConverter
//import github.vatsal.easyweather.WeatherMap
import github.vatsal.easyweather.retrofit.models.*
import github.vatsal.easyweather.retrofit.models.List
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.schedulers.Schedulers
import org.json.JSONObject
import org.json.simple.JSONValue
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.UnknownHostException
import java.nio.charset.Charset
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList

class WeatherService : Service() {

    companion object {
        const val ACTION_UPDATE_WEATHER = "com.zacharee1.boredsigns.action.UPDATE_WEATHER"
        const val API_KEY = "***REMOVED***" //IMPORTANT: Use your own OWM API key here when building for yourself!

        const val EXTRA_TEMP = "temp"
        const val EXTRA_TEMP_EX = "temp_ex"
        const val EXTRA_LOC = "loc"
        const val EXTRA_DESC = "desc"
        const val EXTRA_ICON = "icon"
        const val EXTRA_TIME = "time"

        const val WEATHER_SHOW_TIME = "weather_show_time"
        const val IF_EDIT_LOC ="ifEditLoc"
        const val EDIT_LOC = "editLoc"

        const val WEATHER_NUM = "weather_num"
        const val WHICH_UNIT = "weather_unit"
        const val SHOW_LOADING = "show_loading"

        var numToGet = "6"
    }
    private var lat: Double = 20.0
    private var lon: Double = 110.0
    private var weatherShowTime: Boolean = true
    private var useCelsius: Boolean = true
    private var loc: String = ""
    private var ifEditLoc: Boolean = false
    private var editLoc: String = ""
    private lateinit var prefs: SharedPreferences


    private lateinit var alarmManager: AlarmManager

    private lateinit var alarmIntent: PendingIntent

    private var mLocationClient: LocationClient? = null
    private var myListener:MyLocationListener = MyLocationListener()
    private var mOption = LocationClientOption()

    inner class MyLocationListener : BDAbstractLocationListener() {
        override fun onReceiveLocation(location: BDLocation) {
            //此处的BDLocation为定位结果信息类，通过它的各种get方法可获取定位相关的全部结果
            //以下只列举部分获取经纬度相关（常用）的结果信息
            //更多结果信息获取说明，请参照类参考中BDLocation类中的说明

             lat = location.latitude    //获取纬度信息
             lon = location.longitude    //获取经度信息
            //val radius = location.radius    //获取定位精度，默认值为0.0f

            //val coorType = location.coorType
            //获取经纬度坐标类型，以LocationClientOption中设置过的坐标类型为准

            //val errorCode = location.locType
            //获取定位类型、定位错误返回码，具体信息可参照类参考中BDLocation类中的说明
            loc = location.street + ", " +location.district

            if (lat < 0 || lon < 0 )return

            getWeather(lat,lon)
            mLocationClient!!.stop()
        }
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(p0: Context?, p1: Intent?) {
            onHandleIntent(p1?.action)
        }
    }


    override fun onBind(p0: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        onHandleIntent(intent?.action)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onCreate() {
        super.onCreate()
        startForeground(1, Notification())
        prefs = PreferenceManager.getDefaultSharedPreferences(this)

        alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val intent = Intent(this, this::class.java)
        intent.action = ACTION_UPDATE_WEATHER
        alarmIntent = PendingIntent.getService(this, 0, intent, 0)

        alarmManager.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                SystemClock.elapsedRealtime() + 7200 * 1000,
                7200 * 1000,
                alarmIntent)

        mLocationClient = LocationClient(getApplicationContext())

        mOption.setLocationMode(LocationMode.Battery_Saving)//可选，默认高精度，设置定位模式，高精度，低功耗，仅设备
        mOption.setCoorType("bd09ll")//可选，默认gcj02，设置返回的定位结果坐标系，如果配合百度地图使用，建议设置为bd09ll;
        mOption.setScanSpan(0)//可选，默认0，即仅定位一次，设置发起连续定位请求的间隔需要大于等于1000ms才是有效的
        mOption.setIsNeedAddress(true)//可选，设置是否需要地址信息，默认不需要
        mOption.setIsNeedLocationDescribe(true)//可选，设置是否需要地址描述
        mOption.setNeedDeviceDirect(false)//可选，设置是否需要设备方向结果
        mOption.setLocationNotify(false)//可选，默认false，设置是否当gps有效时按照1S1次频率输出GPS结果
        mOption.setIgnoreKillProcess(true)//可选，默认true，定位SDK内部是一个SERVICE，并放到了独立进程，设置是否在stop的时候杀死这个进程，默认不杀死
        mOption.setIsNeedLocationDescribe(true)//可选，默认false，设置是否需要位置语义化结果，可以在BDLocation.getLocationDescribe里得到，结果类似于“在北京天安门附近”
        mOption.setIsNeedLocationPoiList(true)//可选，默认false，设置是否需要POI结果，可以在BDLocation.getPoiList里得到
        mOption.SetIgnoreCacheException(false)//可选，默认false，设置是否收集CRASH信息，默认收集
        mOption.setOpenGps(false)//可选，默认false，设置是否开启Gps定位
        mOption.setIsNeedAltitude(false)//可选，默认false，设置定位时是否需要海拔信息，默认不需要，除基础定位版本都可用



        LocalBroadcastManager.getInstance(this).registerReceiver(receiver, IntentFilter(ACTION_UPDATE_WEATHER))
        getCurrentLocWeather()
    }



    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
        alarmManager.cancel(alarmIntent)

        stopForeground(true)// 停止前台服务--参数：表示是否移除之前的通知
    }


    private fun onHandleIntent(action: String?) {
        when (action) {
            ACTION_UPDATE_WEATHER -> {
                useCelsius = prefs.getBoolean(WHICH_UNIT, true)
                numToGet = prefs.getString(WEATHER_NUM,"6")
                weatherShowTime = prefs.getBoolean(WEATHER_SHOW_TIME,true)
                ifEditLoc = prefs.getBoolean(IF_EDIT_LOC,false)
                editLoc = prefs.getString(EDIT_LOC,"")

                val extras = Bundle()
                extras.putInt(SHOW_LOADING,1)//为了实现点击即可刷新
                if(isCurrentActivated()) Utils.sendWidgetUpdate(this@WeatherService, WeatherWidget::class.java, extras)
                if(isForecastActivated()) Utils.sendWidgetUpdate(this@WeatherService, WeatherForecastWidget::class.java, extras)

                getCurrentLocWeather()

            }
        }
    }


    //@RequiresPermission(Manifest.permission.ACCESS_FINE_LOCATION)
    private fun getCurrentLocWeather() {
        mLocationClient!!.setLocOption(mOption)
        mLocationClient!!.registerLocationListener(myListener)  //每次都要注册，否则stop之后就会失效
        mLocationClient!!.start()

    }

    private fun getWeather(lat: Double, lon: Double) {
        try {

            if (isCurrentActivated()) {
                CurrentParser().sendRequest(lat.toString(), lon.toString(), object : CurrentCallback {
                    @SuppressLint("CheckResult")
                    override fun onSuccess(model: WeatherResponseModel) {

                            val extras = Bundle()
                            val temp = model.main.temp
                            val tempDouble: Double = if (useCelsius) TempUnitConverter.convertToCelsius(temp) else TempUnitConverter.convertToFahrenheit(temp)
                            //val time = SimpleDateFormat("h:mm aa", Locale.getDefault()).format(Date(model.dt.toLong() * 1000))
                            //val time = SimpleDateFormat("k:mm", Locale.getDefault()).format(Date(System.currentTimeMillis()))
                            val formatted = DecimalFormat("#").format(tempDouble).toString()

                            extras.putString(EXTRA_TEMP, "${formatted}°${if (useCelsius) "C" else "F"}")

                            extras.putString(EXTRA_LOC, loc)
                            extras.putString(EXTRA_DESC, capitalize(model.weather[0].description))
                            //extras.putString(EXTRA_TIME, time)
                            extras.putString(EXTRA_ICON, Utils.parseWeatherIconCode(model.weather[0].id, model.weather[0].icon))

                            extras.putInt(SHOW_LOADING,0)
                            extras.putBoolean(IF_EDIT_LOC,ifEditLoc)
                            extras.putString(EDIT_LOC,editLoc)

                            Utils.sendWidgetUpdate(this@WeatherService, WeatherWidget::class.java, extras)

                    }

                    override fun onFail(message: String) {
                        Toast.makeText(this@WeatherService, String.format(Locale.US, resources.getString(R.string.error_retrieving_weather), message), Toast.LENGTH_SHORT).show()
                    }
                })
            }

            if (isForecastActivated()) {
                ForecastParser().sendRequest(lat.toString(), lon.toString(), object : ForecastCallback {
                    @SuppressLint("CheckResult")
                    override fun onSuccess(model: ForecastResponseModel) {
                        val extras = Bundle()

                        val highTemps = ArrayList<String>()
                        val lowTemps = ArrayList<String>()
                        val times = ArrayList<String>()
                        val icons = ArrayList<String>()

                        model.list
                                .map { it.main.temp_max }
                                .map { if (useCelsius) TempUnitConverter.convertToCelsius(it) else TempUnitConverter.convertToFahrenheit(it) }
                                .map { DecimalFormat("#").format(it).toString() }
                                .mapTo(highTemps) { "$it° ${if (useCelsius) "C" else "F"}" }


                        model.list
                                .map { it.main.temp_min }
                                .map { if (useCelsius) TempUnitConverter.convertToCelsius(it) else TempUnitConverter.convertToFahrenheit(it) }
                                .map { DecimalFormat("#").format(it).toString() }
                                .mapTo(lowTemps) { "$it° ${if (useCelsius) "C" else "F"}" }

                        model.list.mapTo(times) { SimpleDateFormat("k:mm", Locale.getDefault()).format(Date(it.dt.toLong() * 1000)) }

                        model.list.mapTo(icons) { Utils.parseWeatherIconCode(it.weather[0].id, it.weather[0].icon) }

                        extras.putStringArrayList(EXTRA_TEMP, highTemps)
                        extras.putStringArrayList(EXTRA_TEMP_EX, lowTemps)
                        extras.putString(EXTRA_LOC, " ")
                        extras.putStringArrayList(EXTRA_TIME, times)
                        extras.putStringArrayList(EXTRA_ICON, icons)
                        extras.putBoolean(WEATHER_SHOW_TIME, weatherShowTime)
                        extras.putInt(SHOW_LOADING,0)

                        Utils.sendWidgetUpdate(this@WeatherService, WeatherForecastWidget::class.java, extras)
                    }

                    override fun onFail(message: String) {
                        Toast.makeText(this@WeatherService, String.format(Locale.US, resources.getString(R.string.error_retrieving_weather), message), Toast.LENGTH_SHORT).show()
                    }
                })
            }
        } catch (e: Exception) {
            e.printStackTrace()
            val bundle = Bundle()
            bundle.putString("message", e.localizedMessage)
            bundle.putString("stacktrace", Arrays.toString(e.stackTrace))
            Toast.makeText(this, String.format(Locale.US, resources.getString(R.string.error_retrieving_weather), e.localizedMessage), Toast.LENGTH_LONG).show()
        }
    }

    private fun capitalize(string: String): String {
        val builder = StringBuilder()
        val words = string.split(" ")

        for (word in words) {
            if (builder.isNotEmpty()) {
                builder.append(" ")
            }

            builder.append(word[0].toUpperCase()).append(word.substring(1, word.length))
        }

        return builder.toString()
    }


    private fun isCurrentActivated(): Boolean {
        return Utils.isWidgetInUse(WeatherWidget::class.java, this)
    }

    private fun isForecastActivated(): Boolean {
        return Utils.isWidgetInUse(WeatherForecastWidget::class.java, this)
    }



    class CurrentParser {
        private val template = "http://api.openweathermap.org/data/2.5/weather?lat=LAT&lon=LON&lang=zh_cn&appid=$API_KEY"

        @SuppressLint("CheckResult")
        fun sendRequest(lat: String, lon: String, callback: CurrentCallback) {
            val req = template.replace("LAT", lat).replace("LON", lon)

            try {
                Observable.fromCallable {asyncGetJsonString(URL(req))}
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe {
                            if (it.has("cod") && it.getString("cod") != "200") {
                                callback.onFail(it.getString("message"))
                            } else {
                                try {
                                    callback.onSuccess(parseJsonData(it))
                                } catch (e: Exception) {
                                    callback.onFail(e.localizedMessage)
                                }
                            }
                        }
            } catch (e: Exception) {
                callback.onFail(e.localizedMessage)
            }
        }

        private fun parseJsonData(json: JSONObject): WeatherResponseModel {
            val response = WeatherResponseModel()
            val main = Main()
            val weather = Weather()

            weather.icon = json.getJSONArray("weather").getJSONObject(0).getString("icon")
            weather.id = json.getJSONArray("weather").getJSONObject(0).getString("id")
            weather.description = json.getJSONArray("weather").getJSONObject(0).getString("description")
            main.temp = json.getJSONObject("main").getString("temp")

            response.weather = arrayOf(weather)
            response.main = main
            response.dt = json.getString("dt")

            return response
        }

        private fun asyncGetJsonString(url: URL): JSONObject{
            var connection = url.openConnection() as HttpURLConnection

            return try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    if (connection.responseCode == HttpURLConnection.HTTP_MOVED_TEMP
                            || connection.responseCode == HttpURLConnection.HTTP_MOVED_PERM
                            || connection.responseCode == HttpURLConnection.HTTP_SEE_OTHER) {
                        val newUrl = connection.getHeaderField("Location")
                        connection = URL(newUrl).openConnection() as HttpURLConnection
                    }
                }

                val input = if (connection.responseCode < HttpURLConnection.HTTP_BAD_REQUEST) connection.inputStream else connection.errorStream

                input.use { _ ->
                    val reader = BufferedReader(InputStreamReader(input, Charset.forName("UTF-8")))

                    val text = StringBuilder()
                    var cp: Int

                    do {
                        cp = reader.read()
                        if (cp == -1) break

                        text.append(cp.toChar())
                    } while (true)

                    return JSONObject(text.toString())
                }
            } catch (e: Exception) {
                if ((e.cause != null && e.cause is UnknownHostException) || e is UnknownHostException) {
                    JSONObject("{\"cod\":001, \"message\": \"Unknown Host\"}")
                } else {
                    e.printStackTrace()
                    JSONObject("{\"cod\":001, \"message\": \"${JSONValue.escape(e.localizedMessage)}\"}")
                }
            }
        }
    }

    interface CurrentCallback {
        fun onSuccess(model: WeatherResponseModel)
        fun onFail(message: String)
    }

    class ForecastParser {

        private val template = "http://api.openweathermap.org/data/2.5/forecast?lat=LAT&lon=LON&cnt=$numToGet&appid=$API_KEY"

        @SuppressLint("CheckResult")
        fun sendRequest(lat: String, lon: String, callback: ForecastCallback) {
            val req = template.replace("LAT", lat).replace("LON", lon)

            try {
                Observable.fromCallable {asyncGetJsonString(URL(req))}
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe {
                            if (it.has("cod") && it.getString("cod") != "200") {
                                callback.onFail(it.getString("message"))
                            } else {
                                try {
                                    callback.onSuccess(parseJsonData(it))
                                } catch (e: Exception) {
                                    callback.onFail(e.localizedMessage)
                                }
                            }
                        }
            } catch (e: Exception) {
                callback.onFail(e.localizedMessage)
            }
        }

        private fun parseJsonData(json: JSONObject): ForecastResponseModel {
            val response = ForecastResponseModel()
            val list = ArrayList<List>()

            val stuff = json.getJSONArray("list")

            for (i in 0 until stuff.length()) {
                val l = List()
                val main = Main()
                val weather = Weather()

                val s = stuff.getJSONObject(i)

                weather.icon = s.getJSONArray("weather").getJSONObject(0).getString("icon")
                weather.id = s.getJSONArray("weather").getJSONObject(0).getString("id")
                main.temp_max = s.getJSONObject("main").getString("temp")
                main.temp_min = s.getJSONObject("main").getString("temp_min")

                l.weather = arrayOf(weather)
                l.main = main
                l.dt = s.getString("dt")

                list.add(l)
            }

            val listArr = arrayOfNulls<List>(list.size)
            response.list = list.toArray(listArr)

            return response
        }

        private fun asyncGetJsonString(url: URL): JSONObject{
            var connection = url.openConnection() as HttpURLConnection

            return try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    if (connection.responseCode == HttpURLConnection.HTTP_MOVED_TEMP
                            || connection.responseCode == HttpURLConnection.HTTP_MOVED_PERM
                            || connection.responseCode == HttpURLConnection.HTTP_SEE_OTHER) {
                        val newUrl = connection.getHeaderField("Location")
                        connection = URL(newUrl).openConnection() as HttpURLConnection
                    }
                }

                val input = if (connection.responseCode < HttpURLConnection.HTTP_BAD_REQUEST) connection.inputStream else connection.errorStream

                input.use { _ ->
                    val reader = BufferedReader(InputStreamReader(input, Charset.forName("UTF-8")))

                    val text = StringBuilder()
                    var cp: Int

                    do {
                        cp = reader.read()
                        if (cp == -1) break

                        text.append(cp.toChar())
                    } while (true)

                    return JSONObject(text.toString())
                }
            } catch (e: Exception) {
                if ((e.cause != null && e.cause is UnknownHostException) || e is UnknownHostException) {
                    JSONObject("{\"cod\":001, \"message\": \"Unknown Host\"}")
                } else {
                    e.printStackTrace()
                    JSONObject("{\"cod\":001, \"message\": \"${JSONValue.escape(e.localizedMessage)}\"}")
                }
            }
        }
    }

    interface ForecastCallback {
        fun onSuccess(model: ForecastResponseModel)
        fun onFail(message: String)
    }





}
