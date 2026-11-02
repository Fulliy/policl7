package com.example.medicalsystem.controller;

import com.example.medicalsystem.model.RoleEnum;
import com.example.medicalsystem.model.User;
import com.example.medicalsystem.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

@Controller
@RequestMapping("/admin")
@PreAuthorize("hasAnyAuthority('ADMIN')")
@Tag(name = "Администрирование", description = "Управление пользователями администратором")
public class AdminController {

    private final UserService userService;

    public AdminController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/users")
    public String userView(Model model) {
        model.addAttribute("user_list", userService.findAll());
        return "adminUsers";
    }

    @GetMapping("/{id}")
    public String detailView(@PathVariable Integer id, Model model) {
        model.addAttribute("user_object", userService.findById(id));
        return "adminUserInfo";
    }

    @GetMapping("/{id}/update")
    public String updView(@PathVariable Integer id, Model model) {
        model.addAttribute("user_object", userService.findById(id));
        model.addAttribute("roles", RoleEnum.values());
        return "adminUserUpdate";
    }

    @PostMapping("/{id}/update")
    public String updateUser(@PathVariable Integer id,
                             @RequestParam String username,
                             @RequestParam(name = "roles", required = false) String[] roles) {
        Set<RoleEnum> roleSet = EnumSet.noneOf(RoleEnum.class);
        if (roles != null) {
            Arrays.stream(roles)
                    .map(RoleEnum::valueOf)
                    .forEach(roleSet::add);
        }
        User user = userService.findById(id);
        user.setUsername(username);
        userService.updateUserFromAdmin(user, roleSet);
        return "redirect:/admin/" + id;
    }
}
