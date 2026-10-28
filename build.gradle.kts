plugins {
    id("com.android.application") version "8.9.1" apply false
    id("org.jetbrains.kotlin.android") version "2.1.20" apply false
    // Room 코드 생성기 (Kotlin 2.1.20 에 맞는 버전)
    id("com.google.devtools.ksp") version "2.1.20-1.0.32" apply false
}
