package com.example.medicalsystem.service;

import com.example.medicalsystem.model.PatientAffiliation;
import com.example.medicalsystem.model.PatientReferral;
import com.example.medicalsystem.repository.PatientAffiliationRepository;
import com.example.medicalsystem.repository.PatientReferralRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class PatientSectionsService {
    private final PatientReferralRepository referrals;
    private final PatientAffiliationRepository affiliations;

    public PatientSectionsService(PatientReferralRepository referrals,
                                  PatientAffiliationRepository affiliations) {
        this.referrals = referrals;
        this.affiliations = affiliations;
    }

    public List<PatientReferral> referralsForPatient(Integer patientId) {
        return referrals.findByPatient_IdUserOrderByReferralDateDesc(patientId);
    }

    public List<PatientAffiliation> affiliationsForPatient(Integer patientId) {
        return affiliations.findByPatient_IdUserOrderByAttachedAtDesc(patientId);
    }
}
