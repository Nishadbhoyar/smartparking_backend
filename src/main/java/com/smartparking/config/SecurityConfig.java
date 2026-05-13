package com.smartparking.config;

import com.smartparking.security.CustomUserDetailsService;
import com.smartparking.security.JwtAuthFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${cors.allowed-origins}")
    private String allowedOriginsRaw;

    @Autowired
    private JwtAuthFilter jwtAuthFilter;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    /**
     * FIX: JwtAuthFilter is @Component so Spring Boot auto-registers it as a
     * servlet filter AND we add it via addFilterBefore(). This causes it to run
     * twice — Spring Security's SecurityContextHolderFilter clears the context
     * between the two runs, losing the authentication set by the first pass.
     *
     * Setting enabled=false prevents auto-registration. The filter only runs
     * once, inside the Spring Security chain via addFilterBefore().
     */
    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtFilterRegistration(JwtAuthFilter filter) {
        FilterRegistrationBean<JwtAuthFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // FIX: Return 401 (not 403) for unauthenticated requests.
                // Without this, Spring Security returns 403 for both "not logged in" and
                // "logged in but wrong role" — making it impossible to distinguish the two.
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))

                .authenticationProvider(authenticationProvider()) // FIX: explicit wiring

                .authorizeHttpRequests(auth -> auth

                        // ── Public ──────────────────────────────────────────────────────
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/parking-lots/nearby").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/parking-lots/{lotId}").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/valet/eta").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/valet/fare-estimate").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/rental-cars/nearby").permitAll()
                        .requestMatchers("/ws/**").permitAll()
                        // Cashfree webhook must be public — Cashfree calls it without a JWT
                        .requestMatchers(HttpMethod.POST, "/api/payments/webhook").permitAll()

                        // ── Dashboard ────────────────────────────────────────────────────
                        .requestMatchers("/api/dashboard/customer/**").hasRole("CUSTOMER")
                        .requestMatchers("/api/dashboard/admin/**").hasAnyRole("PARKING_LOT_ADMIN", "SUPER_ADMIN")
                        .requestMatchers("/api/dashboard/valet/**").hasRole("VALET")

                        // ── Super Admin ──────────────────────────────────────────────────
                        .requestMatchers("/api/super-admin/**").hasRole("SUPER_ADMIN")

                        // ── Parking Lot Admin ────────────────────────────────────────────
                        .requestMatchers(HttpMethod.POST,   "/api/parking-lots/add").hasAnyRole("PARKING_LOT_ADMIN", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.PUT,    "/api/parking-lots/**").hasAnyRole("PARKING_LOT_ADMIN", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/parking-lots/**").hasAnyRole("PARKING_LOT_ADMIN", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.GET,    "/api/parking-lots/admin/**").hasAnyRole("PARKING_LOT_ADMIN", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.GET,    "/api/slots/lot/**").authenticated()
                        .requestMatchers(HttpMethod.POST,   "/api/slots/**").hasAnyRole("PARKING_LOT_ADMIN", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.PUT,    "/api/slots/**").hasAnyRole("PARKING_LOT_ADMIN", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/slots/**").hasAnyRole("PARKING_LOT_ADMIN", "SUPER_ADMIN")
                        .requestMatchers("/api/analytics/**").hasAnyRole("PARKING_LOT_ADMIN", "SUPER_ADMIN")
                        // FIX: GET /api/features/all must be accessible to lot admins for the feature-picker UI
                        .requestMatchers(HttpMethod.GET,  "/api/features/all").hasAnyRole("PARKING_LOT_ADMIN", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/features/**").hasAnyRole("PARKING_LOT_ADMIN", "SUPER_ADMIN")

                        // ── Promo codes ──────────────────────────────────────────────────
                        .requestMatchers(HttpMethod.POST, "/api/promo/create").hasAnyRole("SUPER_ADMIN", "PARKING_LOT_ADMIN")
                        .requestMatchers(HttpMethod.PUT,  "/api/promo/**").hasAnyRole("SUPER_ADMIN", "PARKING_LOT_ADMIN")
                        .requestMatchers(HttpMethod.GET,  "/api/promo/all").hasAnyRole("SUPER_ADMIN", "PARKING_LOT_ADMIN")
                        .requestMatchers(HttpMethod.GET,  "/api/promo/validate").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/promo/apply").authenticated()

                        // ── Bookings ─────────────────────────────────────────────────────
                        .requestMatchers(HttpMethod.POST, "/api/bookings/reserve").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.GET,  "/api/bookings/customer/**").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.GET,  "/api/bookings/{bookingId}").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/bookings/{bookingId}/cancel").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.POST, "/api/bookings/verify-code").hasAnyRole("PARKING_LOT_ADMIN", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/bookings/checkout").hasAnyRole("PARKING_LOT_ADMIN", "SUPER_ADMIN", "CUSTOMER")
                        .requestMatchers(HttpMethod.GET,  "/api/bookings/lot-admin/**").hasAnyRole("PARKING_LOT_ADMIN", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.GET,  "/api/bookings/lot/**").hasAnyRole("PARKING_LOT_ADMIN", "SUPER_ADMIN")

                        // ── Valet ────────────────────────────────────────────────────────
                        .requestMatchers(HttpMethod.POST, "/api/valet/request").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.POST, "/api/valet/*/request-return").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.GET,  "/api/valet/jobs/available").hasRole("VALET")
                        .requestMatchers(HttpMethod.GET,  "/api/valet/jobs/active").hasRole("VALET")
                        .requestMatchers(HttpMethod.POST, "/api/valet/*/accept").hasRole("VALET")
                        .requestMatchers(HttpMethod.POST, "/api/valet/*/verify-pickup").hasRole("VALET")
                        .requestMatchers(HttpMethod.POST, "/api/valet/*/park").hasRole("VALET")
                        .requestMatchers(HttpMethod.POST, "/api/valet/*/verify-dropoff").hasRole("VALET")
                        .requestMatchers(HttpMethod.POST, "/api/valet/location").hasRole("VALET")
                        .requestMatchers("/api/valet/fare/**").authenticated()
                        .requestMatchers("/api/valet/earnings/{valetId}/payout").hasRole("SUPER_ADMIN")
                        .requestMatchers("/api/valet/earnings/**").hasRole("VALET")
                        .requestMatchers(HttpMethod.GET, "/api/valet/request/*").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/valet/images/*").hasRole("CUSTOMER")
                        // Customer can poll their own active valet job
                        .requestMatchers(HttpMethod.GET, "/api/valet/customer/*/active").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.GET, "/api/valet/customer/*").hasRole("CUSTOMER") // ADD THIS LINE
                        .requestMatchers(HttpMethod.POST, "/api/valet/*/confirm-return").hasRole("CUSTOMER")

                        // ── Rental Cars ──────────────────────────────────────────────────
                        // IMPORTANT: specific /bookings/* rules MUST come before the broad
                        // /rental-cars/** wildcard, or the broad rule matches first and the
                        // specific rules are never evaluated (Spring evaluates top-to-bottom).
                        .requestMatchers(HttpMethod.POST, "/api/rental-cars/bookings/*/verify-pickup").hasAnyRole("CAR_OWNER", "FLEET_ADMIN", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/rental-cars/bookings/*/verify-return").hasAnyRole("CAR_OWNER", "FLEET_ADMIN", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.PUT,  "/api/rental-cars/bookings/*/extend").hasAnyRole("CAR_OWNER", "FLEET_ADMIN", "CUSTOMER", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.GET,  "/api/rental-cars/bookings/**").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/rental-cars/*/book").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.GET,  "/api/rental-cars/customer/**").hasRole("CUSTOMER")
                        .requestMatchers(HttpMethod.POST, "/api/rental-cars/owner/**").hasRole("CAR_OWNER")
                        .requestMatchers(HttpMethod.POST, "/api/rental-cars/company/**").hasRole("FLEET_ADMIN")
                        .requestMatchers(HttpMethod.GET,  "/api/rental-cars/owner/**").hasAnyRole("CAR_OWNER", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.GET,  "/api/rental-cars/company/**").hasAnyRole("FLEET_ADMIN", "SUPER_ADMIN")
                        .requestMatchers(HttpMethod.PUT,  "/api/rental-cars/**").hasAnyRole("CAR_OWNER", "FLEET_ADMIN", "SUPER_ADMIN")

                        // ── Feedback ─────────────────────────────────────────────────────
                        .requestMatchers(HttpMethod.POST, "/api/feedback/submit").authenticated()
                        .requestMatchers(HttpMethod.GET,  "/api/feedback/**").authenticated()

                        // ── Notifications ────────────────────────────────────────────────
                        .requestMatchers("/api/notifications/**").authenticated()


                        // ── Rental Company Registration ──────────────────────────────
                        .requestMatchers(HttpMethod.POST, "/api/rental-company/register").hasRole("FLEET_ADMIN")
                        .requestMatchers(HttpMethod.GET,  "/api/rental-company/my").hasRole("FLEET_ADMIN")
                        .requestMatchers(HttpMethod.GET,  "/api/rental-company/by-admin/**").hasAnyRole("FLEET_ADMIN", "SUPER_ADMIN")

                        // ── User profile ─────────────────────────────────────────────────
                        .requestMatchers(HttpMethod.GET, "/api/users/**").authenticated()
                        .requestMatchers(HttpMethod.PUT, "/api/users/**").authenticated()

                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(Arrays.asList(allowedOriginsRaw.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true);
        return new UrlBasedCorsConfigurationSource() {{
            registerCorsConfiguration("/**", config);
        }};
    }
}