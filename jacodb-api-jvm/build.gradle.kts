dependencies {
    api(project(":jacodb-api-common"))
    api(project(":jacodb-api-storage"))

    api(Libs.asm)
    api(Libs.asm_tree)
    api(Libs.asm_commons)
    api(Libs.asm_util)

    api(Libs.kotlinx_collections_immutable)
    api(Libs.kotlinx_coroutines_core)
    api(Libs.kotlinx_coroutines_jdk8)

    api(Libs.jooq)
}
