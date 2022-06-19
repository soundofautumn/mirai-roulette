plugins {
    val kotlinVersion = "1.5.30"
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.serialization") version kotlinVersion

    id("net.mamoe.mirai-console") version "2.11.1"
}

group = "per.autumn.mirai"
version = "1.1.0"

repositories {
    maven("https://maven.aliyun.com/repository/public")
    mavenCentral()
}
