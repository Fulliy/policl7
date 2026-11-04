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
        context.setVariable("nextAppointment", null);
        context.setVariable("patientDoctors", List.of(
                new com.example.medicalsystem.controller.MainController.DoctorCard(12, "Анна Иванова", "Терапия", "АИ", true)));
        context.setVariable("patientSpecialties", List.of(
                new com.example.medicalsystem.controller.MainController.SpecialtyCard(4, "Терапия", "✚", 1)));
        String html = engine.process("patient_dashboard", context);
        assertTrue(html.contains("Медицина,"));
        assertTrue(html.contains("Ближайшая запись"));
        assertTrue(html.contains("Наши направления"));
        assertTrue(html.contains("Наши врачи"));
        assertTrue(html.contains("Анна Иванова"));
        assertTrue(html.contains("specialtyId=4"));
        assertTrue(html.contains("doctorId=12"));
        assertTrue(html.contains("/appointments/new"));
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

    @Test
    void adminDashboardRendersIndependentOverviewAndEmptyStates() {
        ClassLoaderTemplateResolver templates = new ClassLoaderTemplateResolver();
        templates.setPrefix("templates/");
        templates.setSuffix(".html");
        templates.setCharacterEncoding("UTF-8");
        SpringTemplateEngine engine = new SpringTemplateEngine();
        engine.setTemplateResolver(templates);

        MockServletContext servletContext = new MockServletContext();
        var exchange = JakartaServletWebApplication.buildApplication(servletContext)
                .buildExchange(new MockHttpServletRequest(servletContext), new MockHttpServletResponse());
        WebContext context = new WebContext(exchange, Locale.forLanguageTag("ru"));
        context.setVariable("role", "ADMIN");
        context.setVariable("activePage", "dashboard");
        context.setVariable("currentUsername", "admin");
        context.setVariable("adminName", "admin");
        context.setVariable("welcomeMessage", "Добрый день");
        context.setVariable("today", java.time.LocalDate.now());
        context.setVariable("appointmentsToday", 0L);
        context.setVariable("patientCount", 0L);
        context.setVariable("doctorCount", 0L);
        context.setVariable("upcomingAppointments", List.of());
        context.setVariable("nextAppointment", null);
        context.setVariable("departmentStats", List.of());
        context.setVariable("maxDepartmentCount", 0L);
        context.setVariable("todayAppointments", List.of());

        String html = engine.process("admin_dashboard", context);

        assertTrue(html.contains("Добрый день"));
        assertTrue(html.contains("admin"));
        assertTrue(html.contains("Ближайшие записи"));
        assertTrue(html.contains("Предстоящих записей пока нет."));
        assertTrue(html.contains("Сегодня на приёме"));
        assertTrue(html.contains("Сегодня на приёме записей нет."));
    }
}
