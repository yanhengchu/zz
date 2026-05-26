package cc.ai.zz.feature.overlay.store

import android.graphics.Point
import com.tencent.mmkv.MMKV

class OverlayPositionStore(
    private val mmkv: MMKV = MMKV.defaultMMKV()
) {
    fun readPosition(keyX: String, keyY: String): Point {
        return Point(
            mmkv.decodeInt(keyX, 0),
            mmkv.decodeInt(keyY, 0)
        )
    }

    fun savePosition(keyX: String, keyY: String, x: Int, y: Int) {
        mmkv.encode(keyX, x)
        mmkv.encode(keyY, y)
    }
}
