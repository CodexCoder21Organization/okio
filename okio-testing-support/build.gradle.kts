import java.nio.file.Files
import java.nio.file.attribute.FileTime
import org.jetbrains.kotlin.gradle.tasks.KotlinCompileTool

plugins {
  kotlin("multiplatform")
  id("app.cash.burst")
  id("build-support")
}

kotlin {
  configureOrCreateOkioPlatforms()

  sourceSets {
    all {
      languageSettings.apply {
        optIn("kotlin.time.ExperimentalTime")
      }
    }

    val commonMain by getting {
      dependencies {
        api(projects.okio)
        api(libs.kotlin.test)
        api(libs.burst.runtime)
        api(libs.test.assertk)
      }
    }

    val nonWasmMain by creating {
      dependsOn(commonMain)
      dependencies {
        api(libs.kotlin.time)
        implementation(projects.okioFakefilesystem)
      }
    }

    val zlibMain by creating {
      dependsOn(commonMain)
    }

    if (kmpJsEnabled) {
      val jsMain by getting {
        dependsOn(nonWasmMain)
      }
    }

    val jvmMain by getting {
      dependsOn(nonWasmMain)
      dependsOn(zlibMain)
      dependencies {
        // On the JVM the kotlin-test library resolves to one of three implementations based on
        // which testing framework is in use. JUnit is used downstream, but Gradle can't know that
        // here and thus fails to select a variant automatically. Declare it manually instead.
        api(libs.kotlin.test.junit5)
      }
    }

    if (kmpNativeEnabled) {
      createSourceSet("nativeMain", children = nativeTargets)
        .also { nativeMain ->
          nativeMain.dependsOn(nonWasmMain)
          nativeMain.dependsOn(zlibMain)
        }
    }

    if (kmpWasmEnabled) {
      createSourceSet("wasmMain", children = wasmTargets)
        .also { wasmMain ->
          wasmMain.dependsOn(commonMain)
        }
      val wasmWasiMain by getting {
        dependencies {
          implementation(project(":okio-wasifilesystem"))
        }
      }
    }
  }
}

/*
 * AbstractFileSystemTest expects a file with specific metadata:
 *
 *   path: build/AbstractFileSystemTestFiles/metadata.txt
 *   createdAt: recorded from the host filesystem after preparing the fixture
 *   lastModifiedAt: 2026-02-02T02:02:02Z
 *
 * It would be nicer to do this in the test itself with exec(), but we don't yet have a handy
 * multiplatform API for that.
 */
val touchAbstractFileSystemTestFilesCreatedAt by tasks.registering(Exec::class) {
  val sampleFile = project.file("build/AbstractFileSystemTestFiles/metadata.txt")
  doFirst {
    sampleFile.parentFile.mkdirs()
  }
  environment("TZ" to "UTC")
  commandLine("touch", "-t", "202601010101.01", sampleFile.path)
}
val touchAbstractFileSystemTestFilesModifiedAt by tasks.registering(Exec::class) {
  dependsOn(touchAbstractFileSystemTestFilesCreatedAt)
  val sampleFile = project.file("build/AbstractFileSystemTestFiles/metadata.txt")
  environment("TZ" to "UTC")
  commandLine("touch", "-t", "202602020202.02", sampleFile.path)
  doLast {
    // Linux keeps the original creation time when touch changes the modified time.
    // Record the host value independently so every adapter must return that value.
    val createdAt = Files.getAttribute(sampleFile.toPath(), "creationTime") as FileTime
    project.file("build/AbstractFileSystemTestFiles/created-at.txt")
      .writeText(createdAt.toInstant().toString())
  }
}
tasks.withType<KotlinCompileTool> {
  dependsOn(
    touchAbstractFileSystemTestFilesCreatedAt,
    touchAbstractFileSystemTestFilesModifiedAt,
  )
}
