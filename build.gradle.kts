plugins {
  `maven-publish`
  kotlin("jvm") version "1.3.72"
  kotlin("plugin.serialization") version "1.3.72"
}

group = "com.github.wumo"
version = "1.0.2"

repositories {
  mavenCentral()
}

dependencies {
  implementation(kotlin("stdlib-jdk8", "1.3.72"))
  api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.3.7")
  api("org.jetbrains.kotlinx:kotlinx-serialization-runtime:0.20.0")
  api("com.squareup.okhttp3:okhttp:4.7.2")
  implementation("com.squareup.okhttp3:okhttp-urlconnection:4.7.2")
}

tasks {
  compileKotlin {
    kotlinOptions.jvmTarget = "9"
  }
  compileTestKotlin {
    kotlinOptions.jvmTarget = "9"
  }
}

val kotlinSourcesJar by tasks

publishing {
  publications {
    create<MavenPublication>("maven") {
      from(components["kotlin"])
      artifact(kotlinSourcesJar)
    }
  }
}