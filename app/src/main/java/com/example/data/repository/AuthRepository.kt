package com.example.data.repository

import android.content.Context
import android.net.Uri
import android.util.Patterns
import com.example.data.local.UserDao
import com.example.data.local.UserEntity
import com.example.data.local.UserSettingsDao
import com.example.data.local.UserSettingsEntity
import com.example.data.prefs.SessionPreferences
import com.example.security.PasswordHasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.SecureRandom
import java.util.UUID

sealed class AuthResult<out T> {
    data class Success<T>(val data: T) : AuthResult<T>()
    data class Error(val message: String) : AuthResult<Nothing>()
}

class AuthRepository(
    private val context: Context,
    private val userDao: UserDao,
    private val settingsDao: UserSettingsDao,
    private val sessionPrefs: SessionPreferences
) {
    val activeUserIdFlow: Flow<String?> = sessionPrefs.activeUserIdFlow
    val fallbackThemeFlow = sessionPrefs.fallbackThemeFlow

    fun observeUser(userId: String): Flow<UserEntity?> = userDao.observeUser(userId)

    suspend fun register(
        fullName: String,
        username: String,
        email: String,
        password: String,
        openingBalancePaise: Long
    ): AuthResult<UserEntity> = withContext(Dispatchers.IO) {
        val cleanName = fullName.trim()
        val cleanUsername = username.trim()
        val cleanEmail = email.trim()

        if (cleanName.length < 2) {
            return@withContext AuthResult.Error("Please enter your full name (at least 2 characters).")
        }
        if (cleanUsername.length < 3 || cleanUsername.contains(" ")) {
            return@withContext AuthResult.Error("Username must be at least 3 characters with no spaces.")
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
            return@withContext AuthResult.Error("Please enter a valid email address.")
        }
        if (password.length < 6) {
            return@withContext AuthResult.Error("Password must be at least 6 characters long.")
        }
        if (openingBalancePaise < 0L) {
            return@withContext AuthResult.Error("Opening balance cannot be negative.")
        }

        if (userDao.getUserByUsername(cleanUsername.lowercase()) != null) {
            return@withContext AuthResult.Error("That username is already taken.")
        }
        if (userDao.getUserByEmail(cleanEmail.lowercase()) != null) {
            return@withContext AuthResult.Error("An account with this email already exists.")
        }

        val hashed = PasswordHasher.hashPassword(password)
        val user = UserEntity(
            id = UUID.randomUUID().toString(),
            fullName = cleanName,
            username = cleanUsername,
            usernameLower = cleanUsername.lowercase(),
            email = cleanEmail,
            emailLower = cleanEmail.lowercase(),
            passwordHash = hashed.hashBase64,
            passwordSalt = hashed.saltBase64,
            openingBalancePaise = openingBalancePaise,
            createdAtMillis = System.currentTimeMillis()
        )
        userDao.insertUser(user)
        settingsDao.upsertSettings(UserSettingsEntity(userId = user.id))
        sessionPrefs.setActiveUserId(user.id)
        AuthResult.Success(user)
    }

    suspend fun login(
        usernameOrEmail: String,
        password: String
    ): AuthResult<UserEntity> = withContext(Dispatchers.IO) {
        val key = usernameOrEmail.trim().lowercase()
        if (key.isEmpty() || password.isEmpty()) {
            return@withContext AuthResult.Error("Please enter your username and password.")
        }
        val user = userDao.getUserByUsername(key) ?: userDao.getUserByEmail(key)
            ?: return@withContext AuthResult.Error("Invalid username or password.")

        if (!PasswordHasher.verifyPassword(password, user.passwordHash, user.passwordSalt)) {
            return@withContext AuthResult.Error("Invalid username or password.")
        }

        if (settingsDao.getSettings(user.id) == null) {
            settingsDao.upsertSettings(UserSettingsEntity(userId = user.id))
        }
        sessionPrefs.setActiveUserId(user.id)
        AuthResult.Success(user)
    }

    suspend fun requestPasswordReset(email: String): AuthResult<String> = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase()
        if (!Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
            return@withContext AuthResult.Error("Enter a valid registered email address.")
        }
        val user = userDao.getUserByEmail(cleanEmail)
            ?: return@withContext AuthResult.Error("No account found with that email address.")

        val code = (100_000 + SecureRandom().nextInt(900_000)).toString()
        val expiresAt = System.currentTimeMillis() + 15 * 60_000L
        userDao.updateUser(
            user.copy(
                resetCode = code,
                resetCodeExpiresAtMillis = expiresAt
            )
        )
        AuthResult.Success(code)
    }

    suspend fun completePasswordReset(
        email: String,
        code: String,
        newPassword: String
    ): AuthResult<Unit> = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim().lowercase()
        val user = userDao.getUserByEmail(cleanEmail)
            ?: return@withContext AuthResult.Error("Account not found.")
        val storedCode = user.resetCode
        val expiresAt = user.resetCodeExpiresAtMillis ?: 0L

        if (storedCode.isNullOrBlank() || code.trim() != storedCode) {
            return@withContext AuthResult.Error("Invalid reset verification code.")
        }
        if (System.currentTimeMillis() > expiresAt) {
            return@withContext AuthResult.Error("Verification code expired. Please request a new one.")
        }
        if (newPassword.length < 6) {
            return@withContext AuthResult.Error("New password must be at least 6 characters.")
        }

        val hashed = PasswordHasher.hashPassword(newPassword)
        userDao.updateUser(
            user.copy(
                passwordHash = hashed.hashBase64,
                passwordSalt = hashed.saltBase64,
                resetCode = null,
                resetCodeExpiresAtMillis = null
            )
        )
        AuthResult.Success(Unit)
    }

    suspend fun changePassword(
        userId: String,
        currentPassword: String,
        newPassword: String
    ): AuthResult<Unit> = withContext(Dispatchers.IO) {
        val user = userDao.getUserById(userId)
            ?: return@withContext AuthResult.Error("User session not found.")
        if (!PasswordHasher.verifyPassword(currentPassword, user.passwordHash, user.passwordSalt)) {
            return@withContext AuthResult.Error("Current password is incorrect.")
        }
        if (newPassword.length < 6) {
            return@withContext AuthResult.Error("New password must be at least 6 characters.")
        }
        val hashed = PasswordHasher.hashPassword(newPassword)
        userDao.updateUser(
            user.copy(
                passwordHash = hashed.hashBase64,
                passwordSalt = hashed.saltBase64
            )
        )
        AuthResult.Success(Unit)
    }

    suspend fun updateProfile(
        userId: String,
        fullName: String,
        username: String,
        email: String,
        openingBalancePaise: Long
    ): AuthResult<UserEntity> = withContext(Dispatchers.IO) {
        val existing = userDao.getUserById(userId)
            ?: return@withContext AuthResult.Error("User not found.")
        val cleanName = fullName.trim()
        val cleanUsername = username.trim()
        val cleanEmail = email.trim()

        if (cleanName.length < 2) {
            return@withContext AuthResult.Error("Full name must be at least 2 characters.")
        }
        if (cleanUsername.length < 3 || cleanUsername.contains(" ")) {
            return@withContext AuthResult.Error("Username must be at least 3 characters with no spaces.")
        }
        if (!Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
            return@withContext AuthResult.Error("Please enter a valid email address.")
        }
        if (openingBalancePaise < 0L) {
            return@withContext AuthResult.Error("Opening balance cannot be negative.")
        }

        val byUsername = userDao.getUserByUsername(cleanUsername.lowercase())
        if (byUsername != null && byUsername.id != userId) {
            return@withContext AuthResult.Error("That username is already in use.")
        }
        val byEmail = userDao.getUserByEmail(cleanEmail.lowercase())
        if (byEmail != null && byEmail.id != userId) {
            return@withContext AuthResult.Error("That email is already in use.")
        }

        val updated = existing.copy(
            fullName = cleanName,
            username = cleanUsername,
            usernameLower = cleanUsername.lowercase(),
            email = cleanEmail,
            emailLower = cleanEmail.lowercase(),
            openingBalancePaise = openingBalancePaise
        )
        userDao.updateUser(updated)
        AuthResult.Success(updated)
    }

    suspend fun saveProfilePhoto(userId: String, sourceUri: Uri): AuthResult<String> = withContext(Dispatchers.IO) {
        val existing = userDao.getUserById(userId)
            ?: return@withContext AuthResult.Error("User not found.")
        return@withContext try {
            val avatarsDir = File(context.filesDir, "avatars").apply { mkdirs() }
            val destFile = File(avatarsDir, "avatar_$userId.jpg")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return@withContext AuthResult.Error("Could not read selected photo.")

            val updatedPath = destFile.absolutePath + "?t=" + System.currentTimeMillis()
            userDao.updateUser(existing.copy(profileImagePath = destFile.absolutePath))
            AuthResult.Success(updatedPath)
        } catch (e: Exception) {
            AuthResult.Error(e.localizedMessage ?: "Failed to save profile picture.")
        }
    }

    suspend fun signInOrCreateWithGoogle(
        email: String,
        displayName: String?,
        googleSubjectId: String? = null,
        profilePictureUrl: String? = null
    ): AuthResult<UserEntity> = withContext(Dispatchers.IO) {
        val cleanEmail = email.trim()
        if (!Patterns.EMAIL_ADDRESS.matcher(cleanEmail).matches()) {
            return@withContext AuthResult.Error("Google account did not return a valid email address.")
        }
        val existing = userDao.getUserByEmail(cleanEmail.lowercase())
        if (existing != null) {
            val updated = existing.copy(
                googleSubjectId = googleSubjectId ?: existing.googleSubjectId,
                profileImagePath = existing.profileImagePath ?: profilePictureUrl
            )
            if (updated != existing) {
                userDao.updateUser(updated)
            }
            if (settingsDao.getSettings(updated.id) == null) {
                settingsDao.upsertSettings(UserSettingsEntity(userId = updated.id))
            }
            sessionPrefs.setActiveUserId(updated.id)
            return@withContext AuthResult.Success(updated)
        }

        val cleanName = displayName?.trim()?.takeIf { it.length >= 2 }
            ?: cleanEmail.substringBefore("@")
        val baseUsername = cleanEmail.substringBefore("@")
            .replace(Regex("[^a-zA-Z0-9_]"), "")
            .ifBlank { "finorauser" }
            .take(18)
        var candidateUsername = baseUsername
        var suffix = 1
        while (userDao.getUserByUsername(candidateUsername.lowercase()) != null) {
            candidateUsername = "${baseUsername}_$suffix"
            suffix++
        }

        // Generate an unguessable random password hash for Google-authenticated users
        val randomBytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val hashed = PasswordHasher.hashPassword(
            android.util.Base64.encodeToString(randomBytes, android.util.Base64.NO_WRAP)
        )

        val newUser = UserEntity(
            id = UUID.randomUUID().toString(),
            fullName = cleanName,
            username = candidateUsername,
            usernameLower = candidateUsername.lowercase(),
            email = cleanEmail,
            emailLower = cleanEmail.lowercase(),
            passwordHash = hashed.hashBase64,
            passwordSalt = hashed.saltBase64,
            openingBalancePaise = 0L,
            profileImagePath = profilePictureUrl,
            authProvider = "GOOGLE",
            googleSubjectId = googleSubjectId,
            createdAtMillis = System.currentTimeMillis()
        )
        userDao.insertUser(newUser)
        settingsDao.upsertSettings(UserSettingsEntity(userId = newUser.id))
        sessionPrefs.setActiveUserId(newUser.id)
        AuthResult.Success(newUser)
    }

    suspend fun logout() {
        try {
            kotlinx.coroutines.withTimeoutOrNull(1_500L) {
                val cm = androidx.credentials.CredentialManager.create(context)
                cm.clearCredentialState(androidx.credentials.ClearCredentialStateRequest())
            }
        } catch (ce: kotlinx.coroutines.CancellationException) {
            throw ce
        } catch (_: Exception) {
            // Credential Manager provider may be unavailable in tests or devices without Play Services
        }
        sessionPrefs.setActiveUserId(null)
    }

    suspend fun deleteAccount(userId: String, confirmationInput: String): AuthResult<Unit> = withContext(Dispatchers.IO) {
        val user = userDao.getUserById(userId)
            ?: return@withContext AuthResult.Error("Account not found.")
        if (user.authProvider == "GOOGLE") {
            val clean = confirmationInput.trim()
            if (!clean.equals("DELETE", ignoreCase = true) &&
                !clean.equals(user.email, ignoreCase = true)
            ) {
                return@withContext AuthResult.Error("Type DELETE or your Google email to confirm.")
            }
        } else if (!PasswordHasher.verifyPassword(confirmationInput, user.passwordHash, user.passwordSalt)) {
            return@withContext AuthResult.Error("Password confirmation failed.")
        }
        user.profileImagePath?.takeIf { !it.startsWith("http") }?.let { path ->
            runCatching { File(path).delete() }
        }
        userDao.deleteUserById(userId)
        sessionPrefs.setActiveUserId(null)
        AuthResult.Success(Unit)
    }
}
