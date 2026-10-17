package com.vg.grabber

import android.content.Intent
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.core.content.ContextCompat

class ShutterTileService : TileService() {
    override fun onStartListening() {
        qsTile?.apply { state = if (OverlayService.bubbleVisible) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE; updateTile() }
    }

    override fun onClick() {
        if (!Settings.canDrawOverlays(this)) {
            startActivityAndCollapse(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        }
        val wasOn = OverlayService.bubbleVisible
        val i = Intent(this, OverlayService::class.java)
        if (wasOn) i.action = OverlayService.ACTION_STOP
        ContextCompat.startForegroundService(this, i)
        qsTile?.apply { state = if (wasOn) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE; updateTile() }
    }
}
