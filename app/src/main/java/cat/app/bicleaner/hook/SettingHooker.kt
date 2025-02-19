package cat.app.bicleaner.hook

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import cat.app.bicleaner.property.Bilibili.Companion.instance
import cat.app.bicleaner.utils.callMethodAs
import cat.app.bicleaner.utils.getId
import cat.app.bicleaner.utils.getIntField
import cat.app.bicleaner.utils.getLongField
import cat.app.bicleaner.utils.getObjectField
import cat.app.bicleaner.utils.getObjectFieldOrNullAs
import cat.app.bicleaner.utils.hookAfterMethod
import cat.app.bicleaner.utils.hookBeforeAllConstructors
import cat.app.bicleaner.utils.hookBeforeAllMethods
import cat.app.bicleaner.utils.hookBeforeMethod
import cat.app.bicleaner.utils.new
import cat.app.bicleaner.utils.setIntField
import cat.app.bicleaner.utils.setObjectField
import java.lang.reflect.Constructor
import java.lang.reflect.Proxy

class SettingHooker(classLoader: ClassLoader) : BaseHooker(classLoader) {
    private var showBiCleanerSetting = false

    override fun hook() {
        instance.splashActivityClass?.hookBeforeMethod("onCreate", Bundle::class.java) { param ->
            val self = param.thisObject as Activity
            showBiCleanerSetting = self.intent.hasExtra(SHOW_SETTING_FLAG)
        }

        instance.mainActivityClass?.hookAfterMethod("onResume") { param ->
            if (showBiCleanerSetting) {
                showBiCleanerSetting = false
                //SettingDialog.show(param.thisObject as Activity)
            }
        }

        instance.mainActivityClass?.hookBeforeMethod(
            "onCreate",
            Bundle::class.java
        ) { param ->
            val bundle = param.args[0] as? Bundle
            bundle?.remove("android:fragments")
        }

        instance.drawerClass?.hookAfterMethod(
            "onCreateView",
            LayoutInflater::class.java,
            ViewGroup::class.java,
            Bundle::class.java
        ) { param ->
            val navSettingId = getId("nav_settings")
            val nav =
                param.thisObject.javaClass.declaredFields.first { it.type.name == "android.support.design.widget.NavigationView" }.name
            (param.thisObject.getObjectField(nav)
                ?: param.result).callMethodAs<View>("findViewById", navSettingId)
                .setOnLongClickListener {
                    //SettingDialog.show(param.thisObject.callMethodAs<Activity>("getActivity"))
                    true
                }
        }

        instance.homeCenters().forEach { (c, m) ->
            c?.hookBeforeAllMethods(m) { param ->
                @Suppress("UNCHECKED_CAST")
                val list = param.args[1] as? MutableList<Any>
                    ?: param.args[1]?.getObjectFieldOrNullAs<MutableList<Any>>("moreSectionList")
                    ?: return@hookBeforeAllMethods

                val itemList = list.lastOrNull()?.let {
                    if (it.javaClass != instance.menuGroupItemClass) it.getObjectFieldOrNullAs<MutableList<Any>>(
                        "itemList"
                    ) else list
                } ?: list

                val item = instance.menuGroupItemClass?.new() ?: return@hookBeforeAllMethods
                item.setIntField("id", SETTING_ID)
                    .setObjectField("title", "哔哩清洁姬")
                    .setObjectField(
                        "icon",
                        "https://i0.hdslb.com/bfs/album/276769577d2a5db1d9f914364abad7c5253086f6.png"
                    )
                    .setObjectField("uri", SETTING_URI)
                    .setIntField("visible", 1)
                itemList.forEach {
                    if (try {
                            it.getIntField("id") == SETTING_ID
                        } catch (t: Throwable) {
                            it.getLongField("id") == SETTING_ID.toLong()
                        }
                    ) return@hookBeforeAllMethods
                }
                itemList.add(item)
            }
        }

        instance.settingRouterClass?.hookBeforeAllConstructors { param ->
            if (param.args[1] != SETTING_URI) return@hookBeforeAllConstructors
            val routerType = (param.method as Constructor<*>).parameterTypes[3]
            param.args[3] = Proxy.newProxyInstance(
                routerType.classLoader,
                arrayOf(routerType)
            ) { _, method, _ ->
                val returnType = method.returnType
                Proxy.newProxyInstance(
                    returnType.classLoader,
                    arrayOf(returnType)
                ) { _, method2, args ->
                    when (method2.returnType) {
                        Boolean::class.javaPrimitiveType -> false
                        else -> {
                            if (method2.parameterTypes.isNotEmpty() &&
                                method2.parameterTypes[0].name == "android.app.Activity"
                            ) {
                                val currentActivity = args[0] as Activity
                                //SettingDialog.show(currentActivity)
                            }
                            null
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val SHOW_SETTING_FLAG = "bicleaner_show_setting"
        const val SETTING_URI = "bilibili://bicleaner"
        const val SETTING_ID = 1919810
    }
}