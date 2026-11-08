package com.example.medicalsystem;

import com.example.medicalsystem.config.CurrentUserAdvice;
import com.example.medicalsystem.controller.PatientMedicalCardController;
import com.example.medicalsystem.model.MedicalRecord;
import com.example.medicalsystem.model.User;
import com.example.medicalsystem.service.MedicalRecordService;
import com.example.medicalsystem.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.ui.Model;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.time.LocalDateTime;
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

class PatientMedicalCardViewTest {
    private MockMvc mvc;
    private MedicalRecordService medicalRecords;
    private UserService users;
    private User patient;

    @BeforeEach
    void setUp() {
        medicalRecords = mock(MedicalRecordService.class);
        users = mock(UserService.class);
        patient = new User();
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

        mvc = MockMvcBuilders.standaloneSetup(new PatientMedicalCardController(medicalRecords, users))
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
    void patientWithoutRecordsGetsEmptyCardAndBookingAction() throws Exception {
        when(medicalRecords.findAllForPatient(42)).thenReturn(List.of());

        mvc.perform(get("/medical-card").principal(patientPrincipal()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Медкарта пока не создана")))
                .andExpect(content().string(containsString("Она появится автоматически после первого приёма у врача.")))
                .andExpect(content().string(containsString("href=\"/appointments/new\"")))
                .andExpect(content().string(containsString("Записаться на приём")))
                .andExpect(content().string(not(containsString("href=\"/patient/referrals\""))))
                .andExpect(content().string(containsString("href=\"/patient/affiliations\"")));

        verify(medicalRecords).findAllForPatient(42);
    }

    @Test
    void patientWithRecordsSeesTheirMedicalHistoryInsteadOfEmptyState() throws Exception {
        MedicalRecord record = new MedicalRecord();
        record.setDiagnosis("Плановый осмотр");
        record.setTreatment("Лечение по назначению врача");
        record.setRecommendations("Повторный приём через месяц");
        record.setCreatedDate(LocalDateTime.of(2026, 11, 5, 10, 0));
        when(medicalRecords.findAllForPatient(42)).thenReturn(List.of(record));

        mvc.perform(get("/medical-card").principal(patientPrincipal()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Плановый осмотр")))
                .andExpect(content().string(containsString("Лечение по назначению врача")))
                .andExpect(content().string(containsString("Повторный приём через месяц")))
                .andExpect(content().string(not(containsString("Медкарта пока не создана"))));

        verify(medicalRecords).findAllForPatient(42);
    }

    private UsernamePasswordAuthenticationToken patientPrincipal() {
        var principal = new org.springframework.security.core.userdetails.User("patient@example.com", "unused",
                List.of(new SimpleGrantedAuthority("PATIENT")));
        return new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }
}
