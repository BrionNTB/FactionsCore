plugins {
    java
    id("io.github.goooler.shadow") version "8.1.7"
}

group = "net.factionscore"
version = "0.1.0"

java.sourceCompatibility = JavaVersion.VERSION_21
java.targetCompatibility = JavaVersion.VERSION_21

dependencies {
    compileOnly("org.powernukkitx:powernukkitx")
    // Needed to reference protocol-level enums (e.g. AnimatePacket.Action) surfaced through
    // engine event getters; the engine itself only depends on this as `implementation`, so it
    // isn't exposed transitively to plugin modules.
    compileOnly("org.powernukkitx.protocol:bedrock-connection:3.0.0.Beta7-Debug-SNAPSHOT")
    // GameplaySettings (OkaeriConfig) is only an `implementation` dep of the engine, so its class
    // needs to be on our compile classpath too to reference gameplaySettings() return type.
    compileOnly("eu.okaeri:okaeri-configs-core:5.0.1")

    // Buycraft/Tebex command-queue polling uses a small HTTP client + JSON parsing.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.11.0")

    // Persistence for factions/land/anti-cheat data.
    implementation("org.xerial:sqlite-jdbc:3.46.1.3")

    testImplementation(platform("org.junit:junit-bom:5.10.2"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.test {
    useJUnitPlatform()
}

tasks.shadowJar {
    archiveBaseName.set("FactionsCore")
    archiveClassifier.set("")
    relocate("okhttp3", "net.factionscore.libs.okhttp3")
    relocate("okio", "net.factionscore.libs.okio")
    relocate("com.google.gson", "net.factionscore.libs.gson")
    // org.sqlite is deliberately NOT relocated: sqlite-jdbc registers itself with JDBC's
    // DriverManager via a service file and extracts a native library from a hardcoded
    // org/sqlite/native resource path, both of which break under relocation ("No suitable
    // driver found for jdbc:sqlite:..."). Nothing else on the server bundles sqlite, so
    // there's no conflict to guard against.
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
