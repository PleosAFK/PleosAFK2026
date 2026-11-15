# Unity Integration Guide (`pleos-afk-unity`)

## 1. AAR 빌드
```bash
./gradlew :pleos-afk-unity:assembleRelease
```
생성 위치:
`pleos-afk-unity/build/outputs/aar/pleos-afk-unity-release.aar`

## 2. Unity 프로젝트 배치
1. `pleos-afk-unity-release.aar` 파일을 Unity 프로젝트의 `Assets/Plugins/Android/`에 복사합니다.
2. `unity-sample/PleosAfkBridge.cs`를 Unity 프로젝트의 `Assets/Scripts/`에 복사하고 씬의 GameObject에 붙입니다.

## 3. Gradle 템플릿에 Pleos SDK 의존성 추가
`pleos-afk-unity`는 UI/의존성 충돌을 피하기 위해 `NaviHelper`와 `Vehicle`을 `compileOnly`로 참조합니다.
Unity `Project Settings > Player > Android > Publishing Settings`에서 Custom Gradle Template을 켠 뒤 아래 내용을 추가하세요.

### `settingsTemplate.gradle` (또는 `baseProjectTemplate.gradle`)
```groovy
dependencyResolutionManagement {
    repositories {
        maven { url "https://nexus-playground.pleos.ai/repository/maven-releases/" }
    }
}
```

### `mainTemplate.gradle`
```groovy
dependencies {
    implementation "ai.pleos.playground:NaviHelper:3.0.5"
    implementation "ai.pleos.playground:Vehicle:2.3.2"
    implementation "androidx.core:core-ktx:1.16.0"
}
```
