package cc.ai.zz.app

import android.app.Application
import android.view.Gravity
import cc.ai.zz.feature.ocr.rule.OcrRuleRepository
import com.hjq.toast.Toaster
import com.tencent.mmkv.MMKV

class MyApp : Application() {

    companion object {
        lateinit var context: MyApp
    }

    override fun onCreate() {
        super.onCreate()
        context = this
        Toaster.init(this)
        Toaster.setGravity(Gravity.TOP, 0, 0)
        MMKV.initialize(this)
        OcrRuleRepository(this).ensureExternalRulesFileExists()
    }
}
