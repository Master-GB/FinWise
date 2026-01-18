# FinWise - Personal Finance Manager


FinWise is a comprehensive personal finance management application designed to help users track their expenses, manage budgets, and gain valuable insights into their spending habits.

## ✨ Features

### 💰 Financial Management
- Track income and expenses
- Categorize transactions
- Set and monitor budgets
- Multi-currency support
- Financial analytics and reports

### 🔐 User Authentication
- Secure sign up and login
- Email verification
- Password reset functionality
- User profile management

### 📊 Dashboard & Analytics
- Visual spending overview
- Category-wise expense breakdown
- Monthly/Weekly/Daily transaction views
- Budget progress tracking

### 🔔 Smart Notifications
- Bill payment reminders
- Budget limit alerts
- Weekly/Monthly financial summaries

## 🛠 Technical Stack

- **Language**: Kotlin
- **Minimum SDK**: 24 (Android 7.0)
- **Architecture**: MVVM (Model-View-ViewModel)
- **Database**: Room Persistence Library
- **UI**: Material Design 3 Components
- **Dependency Injection**: Hilt
- **Asynchronous**: Kotlin Coroutines & Flow
- **Build System**: Gradle with Kotlin DSL

## 🚀 Getting Started

### Prerequisites
- Android Studio Giraffe or later
- Android SDK 34
- Kotlin 1.9.0 or later
- Gradle 8.0 or later

### Installation
1. Clone the repository:
   ```bash
   git clone [https://github.com/Master-GB/FinWise.git]

### 🏗 Project Structure
   app/
├── src/
│   ├── main/
│   │   ├── java/com/example/finwise_lab/
│   │   │   ├── data/            # Data layer
│   │   │   │   ├── local/       # Room database and DAOs
│   │   │   │   ├── remote/      # API clients and data sources
│   │   │   │   └── repository/  # Repository implementations
│   │   │   │
│   │   │   ├── di/              # Dependency injection modules
│   │   │   ├── domain/          # Business logic and use cases
│   │   │   ├── ui/              # UI components
│   │   │   │   ├── theme/       # App theming
│   │   │   │   ├── components/  # Reusable UI components
│   │   │   │   └── screens/     # Feature screens
│   │   │   │
│   │   │   └── utils/           # Utility classes and extensions
│   │   │
│   │   └── res/                 # Resources
│   │       ├── drawable/        # Vector assets
│   │       ├── font/            # Custom fonts
│   │       ├── navigation/      # Navigation graphs
│   │       └── values/          # Colors, strings, styles
│   │
│   ├── test/                    # Unit tests
│   └── androidTest/             # Instrumented tests
│
├── build.gradle                 # App level build configuration
└── proguard-rules.pro           # ProGuard rules for release builds
