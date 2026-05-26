plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation(project(":budgetflow-spring-boot-starter"))

    implementation("org.springframework.boot:spring-boot-starter-web")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.register<JavaExec>("runDashboardComparison") {
    group = "application"
    description = "Runs the naive vs adaptive dashboard comparison harness."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.budgetflow.demo.fintech.benchmark.DashboardComparisonHarness")
}

tasks.register<JavaExec>("runDashboardWalkthrough") {
    group = "application"
    description = "Prints the preferred BudgetFlow demo and evaluation walkthrough."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.budgetflow.demo.fintech.benchmark.DashboardWalkthrough")
}

tasks.register<JavaExec>("runAgentTurnDemo") {
    group = "application"
    description = "Runs the minimal AgentWorkSpec demo: one agent turn under healthy, constrained-budget, and pressure-spike scenarios."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.budgetflow.demo.fintech.agent.AgentTurnDemo")
}

tasks.register<JavaExec>("runAgentCoordinationDemo") {
    group = "application"
    description = "Runs boundary-case agent orchestration scenarios: multi-step coordination, degraded-cascade, and balanced vs latency_first profile comparison."
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("com.budgetflow.demo.fintech.agent.AgentCoordinationDemo")
}
