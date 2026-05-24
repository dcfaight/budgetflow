plugins {
    `java-library`
    id("io.spring.dependency-management")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.5.0")
    }
}

dependencies {
    api(project(":budgetflow-core"))
    api("org.springframework.boot:spring-boot-starter")
    runtimeOnly(project(":budgetflow-autoconfigure"))
}
