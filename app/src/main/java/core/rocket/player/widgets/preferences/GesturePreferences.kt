package core.rocket.player.widgets.preferences

import core.rocket.player.widgets.enums.*
import core.rocket.player.widgets.utils.TinyDB

class GesturePreferences(preferenceStore: TinyDB) {
  val doubleTapToSeekDuration = preferenceStore.getInt("double_tap_to_seek_duration", 10)
  val leftSingleActionGesture = preferenceStore.getEnum("left_double_tap_gesture", SingleActionGesture.Seek)
  val centerSingleActionGesture = preferenceStore.getEnum("center_drag_gesture", SingleActionGesture.PlayPause)
  val rightSingleActionGesture = preferenceStore.getEnum("right_drag_gesture", SingleActionGesture.Seek)

  val mediaPreviousGesture = preferenceStore.getEnum("meda_previous_gesture", SingleActionGesture.Seek)
  val mediaPlayGesture = preferenceStore.getEnum("media_play_gesture", SingleActionGesture.PlayPause)
  val mediaNextGesture = preferenceStore.getEnum("media_next_gesture", SingleActionGesture.Seek)
}
