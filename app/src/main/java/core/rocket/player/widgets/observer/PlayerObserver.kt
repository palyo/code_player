package core.rocket.player.widgets.observer

import core.rocket.player.activities.PlayerActivity
import `is`.xyz.mpv.MPVLib

class PlayerObserver(
  private val activity: PlayerActivity
) : MPVLib.EventObserver {
  override fun eventProperty(property: String) {
    activity.runOnUiThread { activity.onObserverEvent(property) }
  }

  override fun eventProperty(property: String, value: Long) {
    activity.runOnUiThread { activity.onObserverEvent(property, value) }
  }

  override fun eventProperty(property: String, value: Boolean) {
    activity.runOnUiThread { activity.onObserverEvent(property, value) }
  }

  override fun eventProperty(property: String, value: String) {
    activity.runOnUiThread { activity.onObserverEvent(property, value) }
  }

  override fun eventProperty(property: String, value: Double) {
    activity.runOnUiThread { activity.onObserverEvent(property, value) }
  }

  override fun event(eventId: Int) {
    activity.runOnUiThread { activity.event(eventId) }
  }
}
