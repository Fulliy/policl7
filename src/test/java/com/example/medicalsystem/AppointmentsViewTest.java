package com.example.medicalsystem;

import com.example.medicalsystem.config.CurrentUserAdvice;
import com.example.medicalsystem.controller.AppointmentController;
import com.example.medicalsystem.model.Appointment;
import com.example.medicalsystem.model.User;
import com.example.medicalsystem.service.AppointmentService;
import com.example.medicalsystem.service.DepartmentService;
import com.example.medicalsystem.service.ServiceEntityService;
import com.example.medicalsystem.service.UserService;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.core.MethodParameter;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.spring6.view.ThymeleafViewResolver;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AppointmentsViewTest {
    private MockMvc mvc;
    private AppointmentService appointments;

    @BeforeEach
    void setUp() {
        appointments = mock(AppointmentService.class);
        UserService users = mock(UserService.class);
        User user = new User();
        user.setIdUser(1);
        when(users.findByUsername(any())).thenReturn(user);

        ClassLoaderTemplateResolver templates = new ClassLoaderTemplateResolver();
        templates.setPrefix("templates/");
        templates.setSuffix(".html");
        templates.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(templates);
        ThymeleafViewResolver views = new ThymeleafViewResolver();
        views.setTemplateEngine(engine);
        views.setCharacterEncoding("UTF-8");
        mvc = MockMvcBuilders.standaloneSetup(new AppointmentController(appointments, users,
                        mock(DepartmentService.class), mock(ServiceEntityService.class), mock(Validator.class)))
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
    void staffViewRendersDetailsEscapesTextAndPreservesPagination() throws Exception {
        Appointment appointment = new Appointment();
        appointment.setIdAppointment(7);
        appointment.setComplaint("<script>alert(1)</script>");
        appointment.setStatus("Завершен");
        when(appointments.searchAdmin(eq("complaint"), eq("test"), eq(null), eq(1)))
                .thenReturn(new PageImpl<>(List.of(appointment), PageRequest.of(0, 1), 2));

        mvc.perform(get("/appointments").param("searchField", "complaint").param("searchValue", "test")
                        .principal(principal("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/appointments/7/edit")))
                .andExpect(content().string(containsString("&lt;script&gt;alert(1)&lt;/script&gt;")))
                .andExpect(content().string(containsString("page=2&amp;searchField=complaint&amp;searchValue=test")));
    }

    @Test
    void patientViewHidesAdministrativeAndMutationActions() throws Exception {
        Appointment appointment = new Appointment();
        appointment.setIdAppointment(8);
        appointment.setStatus("Запланирован");
        when(appointments.searchForUser(eq(1), any(), any(), anyInt(), any()))
                .thenReturn(new PageImpl<>(List.of(appointment)));

        mvc.perform(get("/appointments").principal(principal("PATIENT")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/appointments/8/edit")))
                .andExpect(content().string(containsString("Перенести")))
                .andExpect(content().string(containsString("Отменить")))
                .andExpect(content().string(not(containsString("href=\"/users"))))
                .andExpect(content().string(containsString("Мои записи")))
                .andExpect(content().string(not(containsString("#8"))))
                .andExpect(content().string(not(containsString("По статусу"))));
    }

    @Test
    void emptyResultsRenderHelpfulState() throws Exception {
        when(appointments.searchAdmin(any(), any(), any(), anyInt())).thenReturn(new PageImpl<>(List.of()));
        mvc.perform(get("/appointments").principal(principal("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("По заданным условиям записей нет")));
    }

    @Test
    void patientBookingFormRendersSlotCalendarAndSeparateServiceMode() throws Exception {
        mvc.perform(get("/appointments/new").principal(principal("PATIENT")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Приём врача")))
                .andExpect(content().string(containsString("Услуги")))
                .andExpect(content().string(containsString("calendarGrid")))
                .andExpect(content().string(containsString("serviceTotal")));
    }

    private UsernamePasswordAuthenticationToken principal(String role) {
        var user = new org.springframework.security.core.userdetails.User("test@example.com", "unused",
                List.of(new SimpleGrantedAuthority(role)));
        return new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
    }
}
