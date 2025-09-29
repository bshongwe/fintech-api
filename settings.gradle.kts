rootProject.name = "fintech-api"
include(
    "libs:commons",
    "services:auth-service",
    "services:account-service",
    "services:admin-dashboard",
    "services:compliance-service",
    "services:fraud-detection-service",
    "services:ledger-service",
    // "services:mobile-sdk", // Temporarily excluded due to compilation issues
    "services:notification-service",
    "services:payment-service",
    "services:reporting-service"
)
