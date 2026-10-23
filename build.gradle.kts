plugins {
    java
    id("com.google.protobuf") version "0.9.4"
    id("com.gradleup.shadow") version "9.6.1"
    id("com.diffplug.spotless") version "8.10.4"
}

group = "com.ccnr"
version = "0.1.0"

val grpcVersion = "1.66.0"
val protobufVersion = "3.25.5"
val velocityVersion = "3.4.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("com.velocitypowered:velocity-api:$velocityVersion")

    implementation("io.grpc:grpc-netty-shaded:$grpcVersion")
    implementation("io.grpc:grpc-protobuf:$grpcVersion")
    implementation("io.grpc:grpc-stub:$grpcVersion")
    implementation("com.google.protobuf:protobuf-java:$protobufVersion")
    compileOnly("org.apache.tomcat:annotations-api:6.0.53")
    compileOnly("org.slf4j:slf4j-api:2.0.13")
    compileOnly("javax.inject:javax.inject:1")

    testImplementation("org.slf4j:slf4j-api:2.0.13")
    testRuntimeOnly("org.slf4j:slf4j-simple:2.0.13")
    testImplementation(platform("org.junit:junit-bom:5.10.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

protobuf {
    protoc { artifact = "com.google.protobuf:protoc:$protobufVersion" }
    plugins {
        create("grpc") { artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion" }
    }
    generateProtoTasks {
        all().configureEach { plugins { create("grpc") } }
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(17)
    options.compilerArgs.addAll(listOf("-Xlint:all", "-Werror"))
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}

spotless {
    java {
        target("src/**/*.java")
        googleJavaFormat("1.24.0")
        licenseHeaderFile("gradle/spotless/license-header.txt")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

tasks.shadowJar {
    archiveClassifier.set("")
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    relocate("com.google.protobuf", "com.ccnr.libs.protobuf")
    relocate("com.google.common", "com.ccnr.libs.guava")
    relocate("io.grpc", "com.ccnr.libs.grpc")
    relocate("io.perfmark", "com.ccnr.libs.perfmark")
    mergeServiceFiles()
    exclude("META-INF/LICENSE.txt")
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
