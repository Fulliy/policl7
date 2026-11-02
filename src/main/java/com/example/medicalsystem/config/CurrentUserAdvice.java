package com.example.medicalsystem.config;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Set;
import java.util.stream.Collectors;

@ControllerAdvice(annotations = Controller.class)
public class CurrentUserAdvice {

    @ModelAttribute("isAuthenticated")
    public boolean isAuthenticated(Authentication authentication) {
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }

    @ModelAttribute("currentUsername")
    public String currentUsername(Authentication authentication) {
        return isAuthenticated(authentication) ? authentication.getName() : null;
    }

    @ModelAttribute("currentUserEmail")
    public String currentUserEmail(Authentication authentication) {
        return currentUsername(authentication);
    }

    @ModelAttribute("currentUserRoles")
    public Set<String> currentUserRoles(Authentication authentication) {
        if (!isAuthenticated(authentication)) {
            return Set.of();
        }
        return authentication.getAuthorities().stream()
                .map(authority -> authority.getAuthority())
                .collect(Collectors.toSet());
    }

    @ModelAttribute("role")
    public String role(Authentication authentication) {
        Set<String> roles = currentUserRoles(authentication);
        if (roles.contains("ADMIN")) return "ADMIN";
        if (roles.contains("DOCTOR")) return "DOCTOR";
        if (roles.contains("PATIENT")) return "PATIENT";
        return "";
    }

    @ModelAttribute("isAdmin")
    public boolean isAdmin(Authentication authentication) {
        return currentUserRoles(authentication).contains("ADMIN");
    }

    @ModelAttribute("isDoctor")
    public boolean isDoctor(Authentication authentication) {
        return currentUserRoles(authentication).contains("DOCTOR");
    }

    @ModelAttribute("isPatient")
    public boolean isPatient(Authentication authentication) {
        return currentUserRoles(authentication).contains("PATIENT");
    }
}
