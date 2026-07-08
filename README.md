# AKTIV Admin

Android admin app for pushing bills and appointments into AKTIV (Anubhav Life Care clinic).

## Features

### 🏥 Core Functionality

- **Online Test Booking**: Book diagnostic tests with ease
- **User Authentication**: Login/Signup using email or phone number
- **Payment Integration**: Razorpay integration for advance payments
- **Email Notifications**: Automatic email confirmations to patients and clinic
- **Supabase Integration**: Real-time database for bookings and user data

### 📱 User Experience

- **Home Sample Collection**: Convenient doorstep service
- **Multiple Booking Types**:
    - Regular booking (no time guarantee)
    - Pre-book time slots (with advance payment)
    - Pre-book specific doctors (with advance payment)
- **Real-time Booking Status**: Track your appointment status
- **WhatsApp Support**: Direct WhatsApp integration for customer support

### 🔧 Technical Features

- **Modern Android Architecture**: MVVM pattern with Repository
- **Kotlin Coroutines**: Asynchronous operations
- **Navigation Component**: Seamless app navigation
- **Material Design**: Beautiful and intuitive UI
- **Offline Support**: Local caching with Room database

## Tech Stack

- **Language**: Kotlin
- **Architecture**: MVVM (Model-View-ViewModel)
- **Database**: Supabase (PostgreSQL)
- **Authentication**: Supabase Auth
- **Payment Gateway**: Razorpay
- **Email Service**: SMTP with provided credentials
- **UI**: Material Design Components
- **Navigation**: Android Navigation Component
- **Dependency Injection**: Hilt
- **Networking**: Retrofit + OkHttp
- **Image Loading**: Glide

## Configuration

### Environment Variables

The app uses the following configuration:

```kotlin
// Supabase Configuration
SUPABASE_URL = "your_supabase_url"
SUPABASE_ANON_KEY = "your_supabase_anon_key"

// Razorpay Configuration
RAZORPAY_KEY_ID = "your_razorpay_key_id"
RAZORPAY_KEY_SECRET = "your_razorpay_secret"

// Email Configuration
EMAIL_USER = "your_email_user"
EMAIL_PASS = "your_email_app_password"
```

### Contact Information

- **Phone**: +91-9230755875 | +91-9230755870
- **WhatsApp**: +91-9230755876
- **Email**: contact.anubhavlife@gmail.com
- **Website**: www.anubhavlifecare.in

## App Structure

```
app/
├── src/main/java/com/anubhav/app/
│   ├── data/
│   │   ├── model/          # Data classes (User, Test, Booking)
│   │   ├── remote/         # API services (Supabase client)
│   │   └── repository/     # Repository pattern implementation
│   ├── ui/
│   │   ├── home/           # Home screen
│   │   ├── gallery/        # Booking screen (temporary)
│   │   └── slideshow/      # My Bookings screen (temporary)
│   ├── utils/
│   │   ├── PaymentManager  # Razorpay integration
│   │   └── EmailManager    # Email notifications
│   └── MainActivity.kt     # Main activity
├── res/
│   ├── layout/             # XML layouts
│   ├── values/             # Strings, colors, themes
│   └── navigation/         # Navigation graph
└── build.gradle.kts        # Dependencies and configuration
```

## Key Features Implementation

### 1. Test Selection

- Browse 1500+ available tests
- Search functionality
- Category-wise filtering
- Popular tests section

### 2. Booking Process

- Select preferred date and time
- Choose booking type (regular/pre-book)
- Enter patient details
- Review and confirm booking

### 3. Payment Integration

- Razorpay payment gateway
- Advance payment for slot booking
- Secure transaction processing
- Payment confirmation emails

### 4. Email Notifications

- Booking confirmation to patient
- Booking details to clinic (contact.anubhavlife@gmail.com)
- Status update notifications
- Professional email templates

### 5. Database Operations

- User registration and authentication
- Booking creation and updates
- Test catalog management
- Real-time synchronization

## Sample Tests Available

| Test Name | Price | Category | Preparation |
|-----------|-------|----------|-------------|
| Complete Blood Count (CBC) | ₹300 | Blood Test | 12 hours fasting |
| Lipid Profile | ₹800 | Blood Test | 12-14 hours fasting |
| Thyroid Function Test | ₹600 | Hormone Test | No preparation |
| Blood Sugar Fasting | ₹150 | Blood Test | 8-10 hours fasting |
| HbA1c Test | ₹500 | Diabetes | No fasting required |

## Installation & Setup

1. Clone the repository
2. Open in Android Studio
3. Configure environment variables in `build.gradle.kts`
4. Sync Gradle dependencies
5. Build and run the application

## Future Enhancements

- [ ] Real-time chat support
- [ ] Push notifications
- [ ] Report download feature
- [ ] Multi-language support
- [ ] Dark mode theme
- [ ] Advanced test filtering
- [ ] Family member profiles
- [ ] Loyalty program integration

## Contributing

This is a proprietary application for Anubhav Life Care. For any modifications or issues, please
contact the development team.

## License

© 2024 Anubhav Life Care. All rights reserved.

## Support

For technical support or feature requests, please contact:

- Email: contact.anubhavlife@gmail.com
- Phone: +91-9230755875
- WhatsApp: +91-9230755876
