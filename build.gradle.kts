plugins {
    id("java-library")
    id("maven-publish")
}

group = "dev.consti"
version = "2.2.4"

repositories {
    mavenCentral()
}

java {
    withJavadocJar()
    withSourcesJar()

    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencies {
    // api("org.java-websocket:Java-WebSocket:1.5.7")
    api("io.netty:netty-all:4.2.0.Final")
    api("org.json:json:20240303")
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    implementation("io.jsonwebtoken:jjwt-impl:0.12.6")
    implementation("io.jsonwebtoken:jjwt-jackson:0.12.6")
    implementation("org.yaml:snakeyaml:2.0")


    implementation("org.slf4j:slf4j-api:2.0.12")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.12")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.4.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.4.0")
}

tasks.test {
    useJUnitPlatform()
}

tasks.named("build") {
    dependsOn("test")
}

publishing {
    publications {
        create<MavenPublication>("gpr") {
            groupId = project.group.toString()
            artifactId = "foundationlib"
            version = project.version.toString()

            from(components["java"])

        }
    }

    repositories {
        maven {
            name = "GithubPackages"
            url = uri("https://maven.pkg.github.com/objz/FoundationLib")
            credentials {
                username = "objz"
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
