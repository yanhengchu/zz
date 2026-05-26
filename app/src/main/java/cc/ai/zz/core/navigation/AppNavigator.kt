package cc.ai.zz.core.navigation

import android.content.Intent
import cc.ai.zz.app.MyApp
import cc.ai.zz.feature.home.MainActivity

object AppNavigator {
    fun openHome() {
        MyApp.context.startActivity(homeIntent())
    }

    fun homeIntent(): Intent = Intent(MyApp.context, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }
}
