val asmVersion: String by rootProject
val collectionsVersion: String by rootProject
val coroutinesVersion: String by rootProject
val kmetadataVersion: String by rootProject
val jooqVersion: String by rootProject

dependencies {
    api(group = "org.ow2.asm", name = "asm", version = asmVersion)
    api(group = "org.ow2.asm", name = "asm-tree", version = asmVersion)
    api(group = "org.ow2.asm", name = "asm-commons", version = asmVersion)
    api(group = "org.ow2.asm", name = "asm-util", version = asmVersion)
    api(group = "info.leadinglight", name = "jdot", version = "1.0")

    api(group = "org.jetbrains.kotlinx", name = "kotlinx-collections-immutable-jvm", version = collectionsVersion)
    api(group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-core", version = coroutinesVersion)
    api(group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-jdk8", version = coroutinesVersion)

    api(group = "org.jooq", name = "jooq", version = jooqVersion)
    implementation(group = "org.jetbrains.kotlinx", name = "kotlinx-metadata-jvm", version = kmetadataVersion)

}