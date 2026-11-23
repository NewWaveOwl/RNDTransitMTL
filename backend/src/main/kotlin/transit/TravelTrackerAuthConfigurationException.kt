package transit

/** Rejects an optional routing integration whose configured Auth project cannot verify this session. */
internal class TravelTrackerAuthConfigurationException : RuntimeException(
    "The routing server's TravelTracker sign-in project is missing or differs from the app. " +
        "Set its TravelTracker Supabase URL and publishable key to the same project as the app, then restart the shared server."
)
