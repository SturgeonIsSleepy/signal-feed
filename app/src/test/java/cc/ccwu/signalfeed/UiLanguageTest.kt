package cc.ccwu.signalfeed

import org.junit.Assert.assertEquals
import org.junit.Test

class UiLanguageTest {
    @Test fun builtInsTranslateLabelsAndRetainUnknownNames() {
        assertEquals("Home", English.text("首页"))
        assertEquals("首页", Chinese.text("首页"))
        assertEquals("Verstappen", English.text("Verstappen"))
    }
    @Test fun templatesKeepValuesAndTranslateKnownLabels() {
        assertEquals("Select Drivers (3/20)", English.text("选择车手（3/20）"))
        assertEquals("2 mute or allow rules, 1 breaking rules", English.text("2 条屏蔽或保留规则，1 条 Breaking 规则"))
    }
    @Test fun confirmationHandlesEmptyDetailsAndMultilineValues() {
        val language = UiLanguage("fr", "Français", mapOf("{name}\n{detail}\n\n确认安装到对应栏目？" to "{name}\n{detail}\n\nInstaller ?"))
        assertEquals("Test\n\n\nInstaller ?", language.text("Test\n\n\n确认安装到对应栏目？"))
        assertEquals("Test\nA\nB\n\nInstaller ?", language.text("Test\nA\nB\n\n确认安装到对应栏目？"))
        assertEquals("{detail}\nA\n\nInstaller ?", language.text("{detail}\nA\n\n确认安装到对应栏目？"))
    }
}
