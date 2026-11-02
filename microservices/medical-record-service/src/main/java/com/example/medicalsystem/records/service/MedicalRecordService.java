package com.example.medicalsystem.records.service;

import com.example.medicalsystem.records.model.MedicalRecord;
import com.example.medicalsystem.records.model.Prescription;
import com.example.medicalsystem.records.repository.MedicalRecordRepository;
import com.example.medicalsystem.records.repository.PrescriptionRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class MedicalRecordService {
    private final MedicalRecordRepository records;
    private final PrescriptionRepository prescriptions;
    private final JdbcTemplate jdbc;

    public MedicalRecordService(MedicalRecordRepository records, PrescriptionRepository prescriptions, JdbcTemplate jdbc) {
        this.records = records;
        this.prescriptions = prescriptions;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public List<MedicalRecord> recordsVisibleTo(Authentication auth) {
        if (has(auth, "ADMIN")) return records.findAll();
        Integer userId = userId(auth);
        String ownership = has(auth, "DOCTOR") ? "a.id_doctor" : "a.id_user";
        if (!has(auth, "DOCTOR") && !has(auth, "PATIENT")) throw new AccessDeniedException("Недостаточно прав");
        List<Integer> ids = jdbc.query("select m.id_record from medical_records m join appointments a "
                        + "on a.id_appointment=m.id_appointment where " + ownership + "=?",
                (rs, row) -> rs.getInt(1), userId);
        return records.findAllById(ids);
    }

    @Transactional(readOnly = true)
    public MedicalRecord getRecord(Integer id, Authentication auth) {
        MedicalRecord value = requireRecord(id);
        assertAppointmentAccess(value.getAppointmentId(), auth);
        return value;
    }

    public MedicalRecord createRecord(RecordRequest request, Authentication auth) {
        requireStaff(auth);
        assertAppointmentAccess(request.appointmentId(), auth);
        MedicalRecord value = new MedicalRecord();
        value.setAppointmentId(request.appointmentId());
        value.setDiagnosis(request.diagnosis());
        value.setTreatment(request.treatment());
        value.setRecommendations(request.recommendations());
        value.setCreatedDate(LocalDateTime.now());
        return records.save(value);
    }

    public MedicalRecord updateRecord(Integer id, RecordRequest request, Authentication auth) {
        requireStaff(auth);
        MedicalRecord value = requireRecord(id);
        assertAppointmentAccess(value.getAppointmentId(), auth);
        assertAppointmentAccess(request.appointmentId(), auth);
        value.setAppointmentId(request.appointmentId());
        value.setDiagnosis(request.diagnosis());
        value.setTreatment(request.treatment());
        value.setRecommendations(request.recommendations());
        return records.save(value);
    }

    public void deleteRecord(Integer id, Authentication auth) {
        requireStaff(auth);
        MedicalRecord value = requireRecord(id);
        assertAppointmentAccess(value.getAppointmentId(), auth);
        records.delete(value);
    }

    @Transactional(readOnly = true)
    public List<Prescription> prescriptionsVisibleTo(Authentication auth) {
        if (has(auth, "ADMIN")) return prescriptions.findAll();
        Integer userId = userId(auth);
        String ownership = has(auth, "DOCTOR") ? "a.id_doctor" : "a.id_user";
        if (!has(auth, "DOCTOR") && !has(auth, "PATIENT")) throw new AccessDeniedException("Недостаточно прав");
        List<Integer> ids = jdbc.query("select p.id_prescription from prescriptions p "
                        + "join medical_records m on m.id_record=p.id_record "
                        + "join appointments a on a.id_appointment=m.id_appointment where " + ownership + "=?",
                (rs, row) -> rs.getInt(1), userId);
        return prescriptions.findAllById(ids);
    }

    @Transactional(readOnly = true)
    public Prescription getPrescription(Integer id, Authentication auth) {
        Prescription value = requirePrescription(id);
        assertRecordAccess(value.getMedicalRecordId(), auth);
        return value;
    }

    public Prescription createPrescription(PrescriptionRequest request, Authentication auth) {
        requireStaff(auth);
        assertRecordAccess(request.medicalRecordId(), auth);
        requireService(request.serviceId());
        Prescription value = new Prescription();
        apply(value, request);
        return prescriptions.save(value);
    }

    public Prescription updatePrescription(Integer id, PrescriptionRequest request, Authentication auth) {
        requireStaff(auth);
        Prescription value = requirePrescription(id);
        assertRecordAccess(value.getMedicalRecordId(), auth);
        assertRecordAccess(request.medicalRecordId(), auth);
        requireService(request.serviceId());
        apply(value, request);
        return prescriptions.save(value);
    }

    public void deletePrescription(Integer id, Authentication auth) {
        requireStaff(auth);
        Prescription value = requirePrescription(id);
        assertRecordAccess(value.getMedicalRecordId(), auth);
        prescriptions.delete(value);
    }

    private void apply(Prescription value, PrescriptionRequest request) {
        value.setMedicationName(request.medicationName()); value.setDosage(request.dosage());
        value.setFrequency(request.frequency()); value.setDurationDays(request.durationDays());
        value.setMedicalRecordId(request.medicalRecordId()); value.setServiceId(request.serviceId());
    }

    private void assertRecordAccess(Integer recordId, Authentication auth) {
        MedicalRecord record = requireRecord(recordId);
        assertAppointmentAccess(record.getAppointmentId(), auth);
    }

    private void assertAppointmentAccess(Integer appointmentId, Authentication auth) {
        Integer patientId;
        Integer doctorId;
        try {
            var row = jdbc.queryForMap("select id_user,id_doctor from appointments where id_appointment=?", appointmentId);
            patientId = ((Number) row.get("id_user")).intValue();
            doctorId = row.get("id_doctor") == null ? null : ((Number) row.get("id_doctor")).intValue();
        } catch (org.springframework.dao.EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Приём не найден");
        }
        if (has(auth, "ADMIN")) return;
        Integer principalId = userId(auth);
        if (has(auth, "DOCTOR") && principalId.equals(doctorId)) return;
        if (has(auth, "PATIENT") && principalId.equals(patientId)) return;
        throw new AccessDeniedException("Доступ к чужим медицинским данным запрещён");
    }

    private Integer userId(Authentication auth) {
        Integer value = jdbc.query("select id_user from users where username=?",
                rs -> rs.next() ? rs.getInt(1) : null, auth.getName());
        if (value == null) throw new AccessDeniedException("Пользователь не найден");
        return value;
    }

    private void requireService(Integer serviceId) {
        if (serviceId == null || !Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from services where id_service=?)", Boolean.class, serviceId))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Медицинская услуга не найдена");
        }
    }

    private void requireStaff(Authentication auth) {
        if (!has(auth, "ADMIN") && !has(auth, "DOCTOR")) throw new AccessDeniedException("Недостаточно прав");
    }

    private MedicalRecord requireRecord(Integer id) {
        return records.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private Prescription requirePrescription(Integer id) {
        return prescriptions.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private static boolean has(Authentication auth, String role) {
        return auth.getAuthorities().stream().anyMatch(authority -> role.equals(authority.getAuthority()));
    }

    public record RecordRequest(Integer appointmentId, String diagnosis, String treatment, String recommendations) { }
    public record PrescriptionRequest(String medicationName, String dosage, String frequency, Integer durationDays,
                                      Integer medicalRecordId, Integer serviceId) { }
}
