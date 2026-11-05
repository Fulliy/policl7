package com.example.medicalsystem;

import com.example.medicalsystem.config.CurrentUserAdvice;
import com.example.medicalsystem.controller.PatientSectionsController;
import com.example.medicalsystem.model.PatientAffiliation;
import com.example.medicalsystem.model.PatientReferral;
import com.example.medicalsystem.model.User;
import com.example.medicalsystem.service.PatientSectionsService;
import com.example.medicalsystem.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.time.LocalDate;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PatientSectionsViewTest {
    private MockMvc mvc;
    private PatientSectionsService sections;

    @BeforeEach
    void setUp() {
        sections = mock(PatientSectionsService.class);
        UserService users = mock(UserService.class);
        User patient = new User();
        patient.setIdUser(42);
        when(users.findByUsername(any())).thenReturn(patient);

        ClassLoaderTemplateResolver templates = new ClassLoaderTemplateResolver();
        templates.setPrefix("templates/");
        templates.setSuffix(".html");
        templates.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(templates);
        ThymeleafViewResolver views = new ThymeleafViewResolver();
        views.setTemplateEngine(engine);
        views.setCharacterEncoding("UTF-8");

        mvc = MockMvcBuilders.standaloneSetup(new PatientSectionsController(sections, users))
                .setControllerAdvice(new CurrentUserAdvice())
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.hasParameterAnnotation(AuthenticationPrincipal.class);
                    }

                    @Override
                    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                                                  NativeWebRequest request, WebDataBinderFactory binder) {
                        return ((UsernamePasswordAuthenticationToken) request.getUserPrincipal()).getPrincipal();
                    }
                })
                .setViewResolvers(views)
                .build();
    }

    @Test
    void emptyReferralsPageShowsPatientMessageAndBookingLink() throws Exception {
        when(sections.referralsForPatient(42)).thenReturn(List.of());
        mvc.perform(get("/patient/referrals").principal(patientPrincipal()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Направлений пока нет")))
                .andExpect(content().string(containsString("Они появятся здесь, когда врач выдаст вам направление.")))
                .andExpect(content().string(containsString("href=\"/appointments/new\"")));
        verify(sections).referralsForPatient(42);
    }

    @Test
    void referralsPageRendersItemsWhenPresent() throws Exception {
        PatientReferral referral = new PatientReferral();
        referral.setReferralDate(LocalDate.of(2026, 2, 3));
        referral.setTarget("Ультразвуковое исследование");
        referral.setIssuedByName("Елена Викторовна Смирнова");
        referral.setStatus(PatientReferral.ReferralStatus.ACTIVE);
        when(sections.referralsForPatient(42)).thenReturn(List.of(referral));
        mvc.perform(get("/patient/referrals").principal(patientPrincipal()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ультразвуковое исследование")))
                .andExpect(content().string(containsString("Елена Викторовна Смирнова")))
                .andExpect(content().string(not(containsString("Направлений пока нет"))));
    }

    @Test
    void emptyAffiliationsPageShowsPatientMessageAndBookingLink() throws Exception {
        when(sections.affiliationsForPatient(42)).thenReturn(List.of());
        mvc.perform(get("/patient/affiliations").principal(patientPrincipal()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Прикреплений пока нет")))
                .andExpect(content().string(containsString("Информация о прикреплении к врачу или медцентру появится здесь после оформления.")))
                .andExpect(content().string(containsString("href=\"/appointments/new\"")));
        verify(sections).affiliationsForPatient(42);
    }

    @Test
    void affiliationsPageRendersItemsWhenPresent() throws Exception {
        PatientAffiliation affiliation = new PatientAffiliation();
        affiliation.setMedicalCenterName("Медцентр «Забота», терапевтическое отделение");
        affiliation.setDoctorName("Алексей Сергеевич Миронов");
        affiliation.setAttachedAt(LocalDate.of(2025, 3, 12));
        affiliation.setStatus(PatientAffiliation.AffiliationStatus.ACTIVE);
        when(sections.affiliationsForPatient(42)).thenReturn(List.of(affiliation));
        mvc.perform(get("/patient/affiliations").principal(patientPrincipal()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Медцентр «Забота», терапевтическое отделение")))
                .andExpect(content().string(containsString("Алексей Сергеевич Миронов")))
                .andExpect(content().string(not(containsString("Прикреплений пока нет"))));
    }

    private UsernamePasswordAuthenticationToken patientPrincipal() {
        var principal = new org.springframework.security.core.userdetails.User("patient@example.com", "unused",
                List.of(new SimpleGrantedAuthority("PATIENT")));
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
