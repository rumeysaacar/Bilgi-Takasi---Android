# Bilgi Takası

Bilgi Takası is a Kotlin-based Android mobile application designed as a knowledge and skill exchange platform. The main idea of the app is to allow users to share the topics they know and find people who can teach them the topics they want to learn.

Users can create an account, complete their profile, add the skills they know and the skills they want to learn, create individual listings, browse other users’ listings, save favorite listings, view possible matches, and start messaging with other users.

The project was developed using Kotlin and Jetpack Compose for the user interface. Firebase Authentication is used for user registration and login, while Cloud Firestore is used to store user profiles, listings, favorites, chats, and messages.

## Features

- User registration and login with Firebase Authentication
- Profile setup after registration
- Editable user profile
- Skill and interest selection
- Create knowledge exchange listings
- Browse and search listings
- Category-based filtering
- Save favorite listings
- Matching screen for possible knowledge exchange
- Messaging system between users
- Firebase Firestore database integration
- Modern UI built with Jetpack Compose

## Technologies Used

- Kotlin
- Android Studio
- Jetpack Compose
- Firebase Authentication
- Cloud Firestore
- Material Design Components
- Kotlin Coroutines

## Project Purpose

The purpose of this project is to create a mobile platform where users can exchange knowledge instead of products or money. For example, a user who knows Excel can teach it to another user and, in return, learn English speaking practice from them. This makes the application useful for students and people who want to improve their skills through mutual learning.

## Database Structure

The application uses Cloud Firestore with the following main collections:

- `users`: Stores user profile information
- `listings`: Stores knowledge exchange listings
- `favorites`: Stores users’ saved listings
- `chats`: Stores chat information between users
- `messages`: Stores messages under each chat

## Developer

This project was developed as a mobile programming course project using Kotlin and Firebase.
