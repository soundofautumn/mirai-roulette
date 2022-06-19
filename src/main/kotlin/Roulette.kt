package per.autumn.mirai

import net.mamoe.mirai.console.data.AutoSavePluginConfig
import net.mamoe.mirai.console.data.value
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.console.plugin.jvm.KotlinPlugin
import net.mamoe.mirai.contact.*
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.event.globalEventChannel
import net.mamoe.mirai.message.data.At
import net.mamoe.mirai.message.data.Message
import net.mamoe.mirai.message.data.MessageChainBuilder
import net.mamoe.mirai.message.data.PlainText

data class GroupRouletteData(
    private var bulletCount: Int,
) {
    init {
        if (bulletCount !in (1..6)) bulletCount = 1
    }

    private val originShotList = arrayOf(
        listOf(true, false, false, false, false, false),
        listOf(true, true, false, false, false, false),
        listOf(true, true, true, false, false, false),
        listOf(true, true, true, true, false, false),
        listOf(true, true, true, true, true, false),
        listOf(true, true, true, true, true, true),
    )

    //当前进度 以 0 作为初始值
    var progress: Int = 0

    // true 代表此处有子弹   false 代表没有
    val shotList = originShotList[bulletCount - 1].shuffled()
}

object RouletteConfig : AutoSavePluginConfig("roulette") {
    val botName: String by value()
    val quotations: MutableList<String> by value()
}

enum class RouletteType(val OperateName: String, val introduction: String) {
    //OperateName 在使用指令时用到 introduction 在游戏介绍时用到
    MUTE("禁言", "禁言"),
    KICK("踢人", "踢出"),
}

object Roulette : KotlinPlugin(
    JvmPluginDescription(
        id = "per.autumn.mirai.roulette",
        version = "1.1.0",
    )
) {
    //使用 groupId 作为 key 储存数据
    private val dataMap = HashMap<Long, GroupRouletteData>()

    //默认为禁言模式
    private var rouletteType: RouletteType = RouletteType.MUTE

    //默认的回复语录
    //来自 https://github.com/InvoluteHell/Pallas-Bot/blob/master/src/plugins/roulette/__init__.py
    private val defaultQuotations = listOf(
        //未打中时的回复  0-4
        "无需退路。",
        "英雄们啊，为这最强大的信念，请站在我们这边。",
        "颤抖吧，在真正的勇敢面前。",
        "哭嚎吧，为你们不堪一击的信念。",
        "现在可没有后悔的余地了。",
        //轮盘介绍  "$1"为当前子弹数 "$2"为当前模式  5
        "这是一把充满荣耀与死亡的左轮手枪，六个弹槽只有$1颗子弹，中弹的那个人将会被$2。勇敢的战士们啊，扣动你们的扳机吧！",
        //手枪卡壳（5%的概率开枪失败）  6
        "我的手中的这把武器，找了无数工匠都难以修缮如新。不......不该如此......",
        //打中时的回复  "@"为在此处 @发送者 7
        "米诺斯英雄们的故事......有喜剧，便也会有悲剧。舍弃了荣耀，@选择回归平凡......",
        //没有权限时的回答  8
        "听啊，悲鸣停止了。这是幸福的和平到来前的宁静。"
    )

    override fun onEnable() {
        RouletteConfig.reload()
        init()
        //更改模式
        globalEventChannel().filterIsInstance<GroupMessageEvent>()
            //需要管理员权限
            .filter { it.permission.isOperator() }
            .subscribeAlways<GroupMessageEvent> {
                RouletteType.values().forEach { type ->
                    if (it.message.contentToString() == it.getBotName() + type.OperateName + "轮盘") {
                        rouletteType = type
                        it.group.sendMessage("轮盘已切换至${rouletteType.OperateName}模式")
                        return@forEach
                    }
                }
            }
        //开启轮盘
        globalEventChannel().filterIsInstance<GroupMessageEvent>()
            .filter { it.message.contentToString().startsWith(it.getBotName() + "轮盘") }
            .filter { !dataMap.containsKey(it.group.id) }
            .subscribeAlways<GroupMessageEvent> {
                val lastChar = it.message.contentToString().trim().last()
                val bulletCount: Int = if (lastChar.isDigit()) lastChar.toString().toInt() else 1
                dataMap[it.group.id] = GroupRouletteData(bulletCount)
                it.group.sendMessage(
                    RouletteConfig.quotations[5].replace(
                        "$1", when (bulletCount) {
                            1 -> "一"
                            2 -> "二"
                            3 -> "三"
                            4 -> "四"
                            5 -> "五"
                            6 -> "六"
                            else -> ""
                        }
                    ).replace("$2", rouletteType.introduction).getMessageWithAt(it.sender)
                )
            }
        //开一次枪
        globalEventChannel().filterIsInstance<GroupMessageEvent>()
            .filter { it.message.contentToString() == it.getBotName() + "开枪" }
            .filter { dataMap.containsKey(it.group.id) }
            .subscribeAlways<GroupMessageEvent> {
                val data = dataMap[it.group.id]!!
                //最后回复的消息
                val replyMsg: String
                //判断此处是否有子弹
                if (data.shotList[data.progress]) {
                    //5% 几率枪卡壳
                    replyMsg = if ((1..100).random() <= 5) {
                        RouletteConfig.quotations[6]
                    } else {
                        if (it.group.botPermission.isOperator()) {
                            when (rouletteType) {
                                RouletteType.MUTE -> it.sender.mute((1..20).random() * 60)
                                RouletteType.KICK -> (it.sender as NormalMember).kick("")
                            }
                            RouletteConfig.quotations[7]
                        } else {
                            RouletteConfig.quotations[8]
                        }
                    }
                    dataMap.remove(it.group.id)
                } else {
                    replyMsg = RouletteConfig.quotations[data.progress] + "(${data.progress + 1}/6)"
                }
                it.group.sendMessage(replyMsg.getMessageWithAt(it.sender))
                data.progress++
            }
    }

    override fun onDisable() = RouletteConfig.save()

    private fun init() {
        if (RouletteConfig.quotations.size != 9) {
            logger.warning("未检测到语录或语录配置错误，已启用默认语录")
            RouletteConfig.quotations.clear()
            RouletteConfig.quotations.addAll(defaultQuotations)
        }
    }

    private fun GroupMessageEvent.getBotName() = RouletteConfig.botName.ifEmpty { this.bot.nameCardOrNick }

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