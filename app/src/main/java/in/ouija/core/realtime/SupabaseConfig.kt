package ouija.app.core.realtime

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.realtime.Realtime

object SupabaseConfig {
    const val SUPABASE_URL = "https://exivynchfxroghrohkee.supabase.co"
    const val SUPABASE_ANON_KEY = "sb_publishable_LdkwFB3rQQXF1_OwQcgM3A_IBfmkhJ1"

    fun createClient(
        url: String = SUPABASE_URL,
        anonKey: String = SUPABASE_ANON_KEY
    ): SupabaseClient {
        return createSupabaseClient(
            supabaseUrl = url,
            supabaseKey = anonKey
        ) {
            install(Realtime)
        }
    }
}
