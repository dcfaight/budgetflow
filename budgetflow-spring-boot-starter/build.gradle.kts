plugins {
    id("io.spring.dependency-management")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.boot:spring-boot-dependencies:3.5.0")
    }
}

dependencies {
    api(project(":budgetflow-core"))
    api(project(":budgetflow-autoconfigure"))
    api("org.springframework.boot:spring-boot-starter")
}
