package me.hufman.androidautoidrive.carapp.navigation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.location.Address
import android.os.Handler
import android.util.Log
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIApplication
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIEvent
import me.hufman.androidautoidrive.carapp.navigation.NavigationTrigger.Companion.TAG

interface NavigationTrigger {
	companion object {
		val TAG = "NavTrigger"
	}

	fun triggerNavigation(destination: Address)
}

class NavigationTriggerApp(app: RHMIApplication): NavigationTrigger {
	companion object {
		fun addressToRHMI(address: Address): String {
			// [lastName];[firstName];[street];[houseNumber];[zipCode];[city];[country];[latitude];[longitude];[poiName]
			val houseAddress = NavigationParser.NUM_MATCHER.matchEntire(address.thoroughfare ?: "")
			val houseNumber = houseAddress?.groupValues?.getOrNull(1) ?: ""
			val street = houseAddress?.groupValues?.getOrNull(2) ?: ""
			val lat = (address.latitude / 360 * Int.MAX_VALUE * 2).toBigDecimal()
			val lng = (address.longitude / 360 * Int.MAX_VALUE * 2).toBigDecimal()
			val label = address.featureName?.replace(';', ' ') ?: ""
			return ";;$street;$houseNumber;${address.postalCode ?: ""};${address.locality ?: ""};${address.countryCode ?: ""};$lat;$lng;$label"
		}
	}

	val event = app.events.values.filterIsInstance<RHMIEvent.ActionEvent>().first {
		it.getAction()?.asLinkAction()?.actionType == "navigate"
	}
	val model = event.getAction()!!.asLinkAction()!!.getLinkModel()!!.asRaDataModel()!!

	override fun triggerNavigation(destination: Address) {
		val rhmiNav = addressToRHMI(destination)
		try {
			model.value = rhmiNav
			event.triggerEvent()
		} catch (e: Exception) {
			Log.i(TAG, "Error while starting navigation", e)
		}
	}
}

class NavigationTriggerSender(val context: Context): NavigationTrigger {
	override fun triggerNavigation(destination: Address) {
		val intent = Intent(NavigationTriggerReceiver.INTENT_NAVIGATION)
				.putExtra(NavigationTriggerReceiver.EXTRA_DESTINATION, destination)
		context.sendBroadcast(intent)
	}
}

class NavigationTriggerReceiver(val trigger: NavigationTrigger): BroadcastReceiver() {
	companion object {
		const val INTENT_NAVIGATION = "me.hufman.androidautoidrive.TRIGGER_NAVIGATION"
		const val EXTRA_DESTINATION = "DESTINATION"
	}
	override fun onReceive(p0: Context?, p1: Intent?) {
		val destination = p1?.getParcelableExtra<Address>(EXTRA_DESTINATION)
		if (destination != null) {
			trigger.triggerNavigation(destination)
		}
	}

	fun register(context: Context, handler: Handler) {
		context.registerReceiver(this, IntentFilter(INTENT_NAVIGATION), null, handler)
	}

	fun unregister(context: Context) {
		try {
			context.unregisterReceiver(this)
		} catch (e: IllegalArgumentException) {
			// duplicate unregister
		}
	}
}