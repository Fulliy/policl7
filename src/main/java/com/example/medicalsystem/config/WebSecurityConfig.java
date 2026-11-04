package com.example.medicalsystem.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.JdbcUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

import javax.sql.DataSource;

@Configuration
@EnableMethodSecurity
public class WebSecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(8);
    }

    @Bean
    public UserDetailsService userDetailsService(DataSource dataSource) {
        JdbcUserDetailsManager manager = new JdbcUserDetailsManager(dataSource);
        manager.setUsersByUsernameQuery(
                "select username, password, active from users where username = ?"
        );
        manager.setAuthoritiesByUsernameQuery(
                "select u.username, ur.roles " +
                        "from users u " +
                        "inner join user_role ur on u.id_user = ur.user_id " +
                        "where u.username = ?"
        );
        return manager;
    }

    @Bean
    public AuthenticationProvider authenticationProvider(DataSource dataSource) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService(dataSource));
        provider.setPasswordEncoder(passwordEncoder());
        return provider;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, DataSource dataSource) throws Exception {
        http
                .authenticationProvider(authenticationProvider(dataSource))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/login", "/registration", "/register", "/access-denied", "/error",
                                "/api/auth/register").permitAll()
                        .requestMatchers("/", "/index").authenticated()
                        .requestMatchers("/admin/**", "/users/**", "/roles/**", "/departments/**", "/specialties/**")
                        .hasAuthority("ADMIN")
                        .requestMatchers("/medicalRecords/**", "/prescriptions/**")
                        .hasAnyAuthority("ADMIN", "DOCTOR")
                        .requestMatchers(HttpMethod.POST, "/services/**")
                        .hasAnyAuthority("ADMIN", "DOCTOR")
                        .requestMatchers("/appointments/**", "/services/**")
                        .authenticated()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/", true)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout")
                        .permitAll()
                )
                .exceptionHandling(exception -> exception.accessDeniedPage("/access-denied"));

        return http.build();
    }
}
