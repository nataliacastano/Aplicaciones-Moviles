package com.example.gestorempresa.di

import android.content.Context
import com.example.gestorempresa.data.auth.PasswordHasher
import com.example.gestorempresa.data.auth.RoomAuthRepository
import com.example.gestorempresa.data.auth.FirebaseAuthRepository
import com.example.gestorempresa.data.auth.FirebaseEmailAuthService
import com.example.gestorempresa.domain.auth.EmailAuthService
import com.example.gestorempresa.data.local.AppDatabase
import com.example.gestorempresa.domain.auth.AuthRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AuthDataModule {
    @Provides @Singleton fun database(@ApplicationContext context: Context): AppDatabase = AppDatabase.getDatabase(context)
    @Provides @Singleton fun hasher() = PasswordHasher()
    @Provides @Singleton fun clock(): Clock = Clock.systemUTC()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AuthBindingModule {
    @Binds @Singleton abstract fun repository(impl: FirebaseAuthRepository): AuthRepository
    @Binds @Singleton abstract fun emailService(impl: FirebaseEmailAuthService): EmailAuthService
}
