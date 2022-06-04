package per.autumn.mirai

import net.mamoe.mirai.console.data.AutoSavePluginConfig
import net.mamoe.mirai.console.data.value
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.contact.Member
import net.mamoe.mirai.contact.PermissionDeniedException
import net.mamoe.mirai.contact.nameCardOrNick
import net.mamoe.mirai.event.GlobalEventChannel
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.message.data.At
import net.mamoe.mirai.message.data.Message
import net.mamoe.mirai.message.data.MessageChainBuilder
import net.mamoe.mirai.message.data.PlainText

data class GroupRouletteData(
    var isStarted: Boolean = false,
    var progress: Int = 0,
    val shotList: List<Boolean> = arrayListOf(true, false, false, false, false, false).shuffled()
)

object RouletteConfig : AutoSavePluginConfig("roulette") {
    val botName: String by value()
    val quotations: MutableList<String> by value()
}

object Roulette : KotlinPlugin(
    JvmPluginDescription(
        id = "per.autumn.mirai.roulette",
        version = "1.0",
    )
) {
    private val dataMap = HashMap<Long, GroupRouletteData>()

    //默认的回复语录
    //来自 https://github.com/InvoluteHell/Pallas-Bot/blob/master/src/plugins/roulette/__init__.py
    private val defaultQuotations = listOf(
        //未打中时的回复  0-4
        "无需退路。",
        "英雄们啊，为这最强大的信念，请站在我们这边。",
        "颤抖吧，在真正的勇敢面前。",
        "哭嚎吧，为你们不堪一击的信念。",
        "现在可没有后悔的余地了。",
        //轮盘介绍  5
        "这是一把充满荣耀与死亡的左轮手枪，六个弹槽只有一颗子弹，中弹的那个人将会被禁言。勇敢的战士们啊，扣动你们的扳机吧！",
        //手枪卡壳（5%的概率开枪失败）  6
        "我的手中的这把武器，找了无数工匠都难以修缮如新。不......不该如此......",
        //打中时的回复  7
        "米诺斯英雄们的故事......有喜剧，便也会有悲剧。舍弃了荣耀，@选择回归平凡......",
        //没有权限时的回答  8
        "听啊，悲鸣停止了。这是幸福的和平到来前的宁静。"
    )

    override fun onEnable() {
        RouletteConfig.reload()
        init()
        GlobalEventChannel.filterIsInstance<GroupMessageEvent>()
            .filter { it.message.contentToString() == RouletteConfig.botName.ifEmpty { it.bot.nameCardOrNick } + "轮盘" }
            .subscribeAlways<GroupMessageEvent> {
                if (!dataMap.containsKey(it.group.id)) {
                    dataMap[it.group.id] = GroupRouletteData()
                    it.group.sendMessage(RouletteConfig.quotations[5].getMessageWithAt(it.sender))
                }
            }
        GlobalEventChannel.filterIsInstance<GroupMessageEvent>()
            .filter { it.message.contentToString() == RouletteConfig.botName.ifEmpty { it.bot.nameCardOrNick } + "开枪" }
            .filter { dataMap.containsKey(it.group.id) }
            .subscribeAlways<GroupMessageEvent> {
                val data = dataMap[it.group.id]!!
                data.progress++
                if (data.shotList[data.progress - 1]) {
                    if ((1..100).random() <= 5) {
                        it.group.sendMessage(RouletteConfig.quotations[6].getMessageWithAt(it.sender))
                    } else {
                        try {
                            it.sender.mute((1..20).random() * 60)
                            it.group.sendMessage(RouletteConfig.quotations[7].getMessageWithAt(it.sender))
                        } catch (e: PermissionDeniedException) {
                            it.group.sendMessage(RouletteConfig.quotations[8].getMessageWithAt(it.sender))
                        }
                    }
                    dataMap.remove(it.group.id)
                } else {
                    it.group.sendMessage(RouletteConfig.quotations[data.progress - 1].getMessageWithAt(it.sender) + "(${data.progress}/6)")
                }
            }
    }

    override fun onDisable() {
        RouletteConfig.save()
    }

    private fun init() {
        if (RouletteConfig.quotations.size != 9) {
            RouletteConfig.quotations.clear()
            RouletteConfig.quotations.addAll(defaultQuotations)
        }
    }

    private fun String.getMessageWithAt(target: Member): Message {
        return if (!this.contains("@")) {
            PlainText(this)
        } else {
            this.split("@").let {
                MessageChainBuilder().append(it[0]).also { builder ->
                    for (i in (1 until it.size)) {
                        builder.append(At(target)).append(it[i])
                    }
                }.build()
            }
        }
    }
}