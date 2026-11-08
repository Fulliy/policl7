package com.example.medicalsystem.repository;

import com.example.medicalsystem.model.ClinicBranch;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ClinicBranchRepository extends JpaRepository<ClinicBranch, Integer> {
    List<ClinicBranch> findAllByOrderByNameAsc();
}
