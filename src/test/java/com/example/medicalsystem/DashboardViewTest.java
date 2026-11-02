package com.example.medicalsystem;

import com.example.medicalsystem.model.User;
import org.junit.jupiter.api.Test;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.util.List;
import java.util.Locale;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DashboardViewTest {

    @Test
    void dashboardUsesPatientLayoutAndPersonalGreeting() {
        ClassLoaderTemplateResolver templates = new ClassLoaderTemplateResolver();
        templates.setPrefix("templates/");
        templates.setSuffix(".html");
        templates.setCharacterEncoding("UTF-8");
        templates.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(templates);

        User patient = new User();
        patient.setFirstName("Анна");
        MockServletContext servletContext = new MockServletContext();
        var exchange = JakartaServletWebApplication.buildApplication(servletContext)
                .buildExchange(new MockHttpServletRequest(servletContext), new MockHttpServletResponse());
        WebContext context = new WebContext(exchange, Locale.forLanguageTag("ru"));
        context.setVariable("patient", patient);
        context.setVariable("patientAppointments", List.of());
        String html = engine.process("patient_dashboard", context);
        assertTrue(html.contains("Здравствуйте, <span>Анна</span>!</h1>"));
        assertTrue(html.contains("Ближайшие записи"));
        assertTrue(html.contains("Записи на приём"));
        assertTrue(!html.contains("Рабочее пространство"));
    }

    @Test
    void doctorDashboardRendersScheduleAndPatientActions() {
        ClassLoaderTemplateResolver templates = new ClassLoaderTemplateResolver();
        templates.setPrefix("templates/");
        templates.setSuffix(".html");
        templates.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(templates);

        User doctor = new User();
        doctor.setFirstName("Рамиз");
        MockServletContext servletContext = new MockServletContext();
        var exchange = JakartaServletWebApplication.buildApplication(servletContext)
                .buildExchange(new MockHttpServletRequest(servletContext), new MockHttpServletResponse());
        WebContext context = new WebContext(exchange, Locale.forLanguageTag("ru"));
        context.setVariable("doctor", doctor);
        context.setVariable("today", java.time.LocalDate.now());
        context.setVariable("todayAppointments", List.of());
        context.setVariable("weekAppointments", List.of());
        context.setVariable("appointmentsToday", 0L);
        context.setVariable("role", "DOCTOR");
        context.setVariable("activePage", "dashboard");
        context.setVariable("currentUsername", "doctor");

        String html = engine.process("doctor_dashboard", context);

        assertTrue(html.contains("Здравствуйте, <span>Рамиз</span>!"));
        assertTrue(html.contains("Расписание на сегодня"));
        assertTrue(html.contains("На сегодня приёмов нет."));
    }
}
