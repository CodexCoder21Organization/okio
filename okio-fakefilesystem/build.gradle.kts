import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinMultiplatform
import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.jetbrains.kotlin.gradle.dsl.JsModuleKind
import org.jetbrains.kotlin.gradle.targets.jvm.KotlinJvmTarget

plugins {
  kotlin("multiplatform")
  id("org.jetbrains.dokka")
  id("com.vanniktech.maven.publish.base")
  id("binary-compatibility-validator")
  id("build-support")
}

kotlin {
  jvm {
  }
  if (kmpJsEnabled) {
    js {
      compilerOptions {
        moduleKind = JsModuleKind.MODULE_UMD
        sourceMap = true
      }
      nodejs {
        testTask {
          useMocha {
            timeout = "30s"
          }
        }
      }
      browser {
      }
    }
  }
  if (kmpNativeEnabled) {
    configureOrCreateNativePlatforms()
  }
  sourceSets {
    all {
      languageSettings.apply {
        optIn("kotlin.time.ExperimentalTime")
      }
    }
    val commonMain by getting {
      dependencies {
        api(libs.kotlin.time)
        implementation("org.jetbrains.kotlinx:atomicfu:0.29.0")
        api(projects.okio)
      }
    }
    val commonTest by getting {
      dependencies {
        implementation(libs.kotlin.test)
      }
    }
    val jvmTest by getting {
      dependencies {
        implementation(libs.kotlin.test.junit5)
        implementation("org.ow2.asm:asm:9.7.1")
      }
    }
    if (kmpWasmEnabled) {
      // Add support for wasmWasi when https://github.com/Kotlin/kotlinx-datetime/issues/324 is resolved.
      configureOrCreateWasmPlatform(wasi = false)
      createSourceSet("wasmMain", parent = commonMain, children = listOf("wasmJs"))
      createSourceSet("wasmTest", parent = commonTest, children = listOf("wasmJs"))
    }

    val nonJvmMain by creating {
      dependsOn(commonMain)
    }
    if (kmpJsEnabled) {
      getByName("jsMain").dependsOn(nonJvmMain)
    }
    if (kmpNativeEnabled) {
      for (childTarget in nativeTargets) {
        get("${childTarget}Main").dependsOn(nonJvmMain)
      }
    }
    if (kmpWasmEnabled) {
      getByName("wasmMain").dependsOn(nonJvmMain)
    }
  }
}

tasks {
  val jvmJar by getting(Jar::class) {
    // BundleTaskConvention() crashes unless there's a 'main' source set.
    sourceSets.create(SourceSet.MAIN_SOURCE_SET_NAME)
    val bndExtension = aQute.bnd.gradle.BundleTaskExtension(this)
    bndExtension.setBnd(
      """
      Export-Package: okio.fakefilesystem
      Automatic-Module-Name: okio.fakefilesystem
      Bundle-SymbolicName: com.squareup.okio.fakefilesystem
      """
    )
    // Call the convention when the task has finished to modify the jar to contain OSGi metadata.
    doLast {
      bndExtension.buildAction()
        .execute(this)
    }
  }
}

configure<MavenPublishBaseExtension> {
  configure(
    KotlinMultiplatform(javadocJar = JavadocJar.Empty())
  )
}

// Keep the public FileHandle work and scheduling regressions in the regular JVM suite.
val jvmTarget = kotlin.targets.getByName("jvm") as KotlinJvmTarget
val compileReviewTests by tasks.registering(JavaCompile::class) {
  source(fileTree("../backports/fakefilesystem-3.4.0/review-tests") { include("*.java") })
  classpath = jvmTarget.compilations.getByName("test").compileDependencyFiles +
    jvmTarget.compilations.getByName("main").output.allOutputs
  destinationDirectory.set(layout.buildDirectory.dir("classes/reviewTests"))
  options.release.set(8)
}
val reviewTestAgent by tasks.registering(Jar::class) {
  from(compileReviewTests.flatMap { it.destinationDirectory })
  archiveClassifier.set("test-work-agent")
  destinationDirectory.set(layout.buildDirectory.dir("test-agent"))
  manifest.attributes["Premain-Class"] = "FakeFileSystemWorkAgent"
}
tasks.named<Test>("jvmTest") {
  dependsOn(reviewTestAgent)
  testClassesDirs += files(compileReviewTests.flatMap { it.destinationDirectory })
  classpath += files(compileReviewTests.flatMap { it.destinationDirectory })
  jvmArgs("-javaagent:${reviewTestAgent.get().archiveFile.get().asFile}")
}
