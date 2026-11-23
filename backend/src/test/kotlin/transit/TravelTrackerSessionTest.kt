package transit

import java.util.Base64
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Separates an auxiliary routing setup failure from a missing traveller session without trusting JWT claims. */
class TravelTrackerSessionTest {
    @Test fun missingConfigurationIsUnavailableForSignedInRequestsAndDoesNotCallProviders() = runBlocking {
        Upstream(false).use { upstream ->
            val api = TravelTrackerApi(NominatimAddresses({ _, _ -> error("Must not call provider") }),
                TravelTrackerPlanner { error("Must not call OTP") }, travelTrackerSession(null, null, upstream))
            assertEquals(401, api.search(null, "City Hall").first)
            val failure = api.search("Bearer traveller-session", "City Hall")
            assertEquals(503, failure.first)
            assertEquals("authentication_unavailable", failure.second["status"].text())
            assertTrue(failure.second["message"].text()!!.contains("same project"))
        }
    }

    @Test fun anotherProjectIssuerFailsAsSetupErrorBeforeSendingBearerToWrongAuthProject() = runBlocking {
        Upstream(false).use { upstream ->
            val payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                """{"iss":"https://traveller-project.supabase.co/auth/v1","sub":"not-a-verified-identity"}""".toByteArray())
            val api = TravelTrackerApi(NominatimAddresses({ _, _ -> error("Must not call provider") }),
                TravelTrackerPlanner { error("Must not call OTP") },
                travelTrackerSession("https://another-project.supabase.co", "sb_publishable_fixture", upstream))
            val failure = api.search("Bearer header.$payload.signature", "City Hall")
            assertEquals(503, failure.first)
            assertFalse(failure.second.toString().contains("traveller-project"))
            assertFalse(failure.second.toString().contains("not-a-verified-identity"))
        }
    }
}
