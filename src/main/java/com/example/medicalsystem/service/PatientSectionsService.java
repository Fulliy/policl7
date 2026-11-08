package com.example.medicalsystem.service;

import com.example.medicalsystem.model.PatientAffiliation;
import com.example.medicalsystem.repository.PatientAffiliationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class PatientSectionsService {
    private final PatientAffiliationRepository affiliations;

    public PatientSectionsService(PatientAffiliationRepository affiliations) {
        this.affiliations = affiliations;
    }

    public List<PatientAffiliation> affiliationsForPatient(Integer patientId) {
        return affiliations.findByPatient_IdUserOrderByAttachedAtDesc(patientId);
    }
}
