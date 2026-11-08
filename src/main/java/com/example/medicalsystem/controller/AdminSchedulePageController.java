package com.example.medicalsystem.controller;

import com.example.medicalsystem.model.RoleEnum;
import com.example.medicalsystem.repository.DoctorScheduleExceptionRepository;
import com.example.medicalsystem.repository.UserRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin/doctor-leaves")
@PreAuthorize("hasAuthority('ADMIN')")
public class AdminSchedulePageController {
    private final DoctorScheduleExceptionRepository exceptions;
    private final UserRepository users;

    public AdminSchedulePageController(DoctorScheduleExceptionRepository exceptions, UserRepository users) {
        this.exceptions = exceptions;
        this.users = users;
    }

    @GetMapping
    public String schedule(Model model) {
        model.addAttribute("doctors", users.findAll().stream()
                .filter(user -> user.getRoles() != null && user.getRoles().contains(RoleEnum.DOCTOR)).toList());
        model.addAttribute("exceptions", exceptions.findAll());
        model.addAttribute("exceptionTypes", com.example.medicalsystem.model.DoctorScheduleException.ExceptionType.values());
        return "adminDoctorLeaves";
    }
}
