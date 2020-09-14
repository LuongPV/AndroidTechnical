package com.pv.trackme.ui.record

import android.content.Context
import android.graphics.Bitmap
import android.location.Location
import android.os.Handler
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.pv.trackme.constant.CommonConstant
import com.pv.trackme.data.db.AppDatabase
import com.pv.trackme.data.db.Session
import com.pv.trackme.domain.LocationHelper
import com.pv.trackme.util.Coroutines
import com.pv.trackme.util.DateTimeUtil
import com.pv.trackme.util.ImageUtil
import timber.log.Timber
import java.util.*

class RecordViewModel(
    private val database: AppDatabase,
    private val handler: Handler,
    private val locationHelper: LocationHelper
) : ViewModel() {
    val distance: MutableLiveData<Double> = MutableLiveData()
    val speed: MutableLiveData<Double> = MutableLiveData()
    val time: MutableLiveData<String> = MutableLiveData()
    val locationUpdateStart: MutableLiveData<Any> = MutableLiveData()
    val locationUpdateStop: MutableLiveData<Any> = MutableLiveData()
    val initLocation: MutableLiveData<Location> = MutableLiveData()
    val updatedLocation: MutableLiveData<Location> = MutableLiveData()
    val dataSave: MutableLiveData<Any> = MutableLiveData()
    val loadingView: MutableLiveData<Any> = MutableLiveData()

    init {
        locationHelper.apply {
            setInitialLocationListener { initLocation.value = it }
            setCurrentLocationListener { updatedLocation.value = it }
            setDistanceListener { distance.value = it }
            setSpeedListener { speed.value = it }
        }
    }

    fun getPreviousLocation() = locationHelper.getPreviousLocation()

    fun start() {
        distance.value = null
        speed.value = null
        time.value = null
        val cal = DateTimeUtil.getNonTimeCalendar()
        time.value = DateTimeUtil.formatDateTime(CommonConstant.TIME_PATTERN_SESSION, cal.timeInMillis)
        tickTime(cal)
        locationUpdateStart.value = null
    }

    private fun tickTime(cal: Calendar) {
        handler.post(object : Runnable {
            override fun run() {
                handler.postDelayed(this, 1000)
                cal.add(Calendar.SECOND, 1)
                time.value = DateTimeUtil.formatDateTime(CommonConstant.TIME_PATTERN_SESSION, cal.timeInMillis)
            }
        })
    }

    fun pause() {
        handler.removeCallbacksAndMessages(null)
        locationUpdateStop.value = null
    }

    fun resume() {
        locationUpdateStart.value = null
        val currentTimestamp = DateTimeUtil.parseDateTime(CommonConstant.TIME_PATTERN_SESSION, time.value!!)
        tickTime(Calendar.getInstance(Locale.getDefault()).apply {
            timeInMillis = currentTimestamp
        })
    }

    fun stop(context: Context, mapSnapshotBitmap: Bitmap?) {
        locationUpdateStop.value = null
        if (mapSnapshotBitmap == null) {
            dataSave.value = null
            return
        }
        Coroutines.main {
            loadingView.value = Any()
            val imagePath = ImageUtil.saveBitmapToFile(
                context,
                mapSnapshotBitmap
            ) ?: return@main
            Timber.d("Done saving map snapshot image to storage location = $imagePath")
            val sessionDAO = database.getSessionDAO()
            sessionDAO.insert(
                Session(
                    imagePath,
                    locationHelper.getTotalDistance(),
                    locationHelper.getAverageSpeed(),
                    DateTimeUtil.parseDateTime(CommonConstant.TIME_PATTERN_SESSION, time.value!!)
                )
            )
            loadingView.value = null
            dataSave.value = Any()
        }
    }

    fun onLocationUpdated(location: Location) {
        locationHelper.onLocationUpdated(location, time.value!!)
    }

}