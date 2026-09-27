# Code Review & Audit Reference Notes (Archived)

Note: This is an archived reference log of findings reviewed across project codebase components.

Critical: security and privacy
1. The friend feature logs into classmates' accounts with a default password.
2. The release build configuration and network security config checks.
3. Link filter intent validation and extra verification.
4. Logcat filtering in release builds.
5. FileProvider scope minimization.

Session, logout and data loss
6. TokenAuthenticator session handling on transient server errors.
7. Navigation reconstitution on session updates.
8. Data isolation between user profiles on shared hardware.
9. Database schema migrations and destructive fallback protection.
10. EncryptedSharedPreferences error recovery without master key deletion.

Medium: functional bugs
- Download directory path resolution across Android 8+.
- Class alarm exact-alarm permission handling on Android 14+.
- State update cancellation handling in Coroutine ViewModels.
- UI status bar appearance synchronization with system theme.
