package com.example.medicalsystem.records.service;

import com.example.medicalsystem.records.model.MedicalRecord;
import com.example.medicalsystem.records.model.Prescription;
import com.example.medicalsystem.records.repository.MedicalRecordRepository;
import com.example.medicalsystem.records.repository.PrescriptionRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MedicalRecordOwnershipTest {
    @Test
    void patientCanReadOwnRecord() {
        MedicalRecord record = record();
        MedicalRecordService service = service(record, 42);
        assertSame(record, service.getRecord(5, authentication("patient", "PATIENT")));
    }

    @Test
    void patientCannotReadAnotherPatientsRecordOrPrescription() {
        MedicalRecord record = record();
        var records = mock(MedicalRecordRepository.class);
        var prescriptions = mock(PrescriptionRepository.class);
        var jdbc = ownershipJdbc(9);
        when(records.findById(5)).thenReturn(Optional.of(record));
        Prescription prescription = new Prescription();
        prescription.setMedicalRecordId(5);
        when(prescriptions.findById(11)).thenReturn(Optional.of(prescription));
        MedicalRecordService service = new MedicalRecordService(records, prescriptions, jdbc);
        var patient = authentication("patient", "PATIENT");

        assertThrows(AccessDeniedException.class, () -> service.getRecord(5, patient));
        assertThrows(AccessDeniedException.class, () -> service.getPrescription(11, patient));
    }

    @Test
    void patientCannotModifyOrDeleteMedicalRecordsOrPrescriptions() {
        var records = mock(MedicalRecordRepository.class);
        var prescriptions = mock(PrescriptionRepository.class);
        var service = new MedicalRecordService(records, prescriptions, ownershipJdbc(9));
        var patient = authentication("patient", "PATIENT");
        var recordRequest = new MedicalRecordService.RecordRequest(88, "diagnosis", "treatment", "recommendations");
        var prescriptionRequest = new MedicalRecordService.PrescriptionRequest("medicine", "dose", "daily", 5, 5, 2);

        assertThrows(AccessDeniedException.class, () -> service.updateRecord(5, recordRequest, patient));
        assertThrows(AccessDeniedException.class, () -> service.deleteRecord(5, patient));
        assertThrows(AccessDeniedException.class, () -> service.updatePrescription(11, prescriptionRequest, patient));
        assertThrows(AccessDeniedException.class, () -> service.deletePrescription(11, patient));
        verify(records, never()).save(org.mockito.ArgumentMatchers.any());
        verify(records, never()).delete(org.mockito.ArgumentMatchers.any());
        verify(prescriptions, never()).save(org.mockito.ArgumentMatchers.any());
        verify(prescriptions, never()).delete(org.mockito.ArgumentMatchers.any());
    }

    private static MedicalRecordService service(MedicalRecord record, int ownerId) {
        var records = mock(MedicalRecordRepository.class);
        var prescriptions = mock(PrescriptionRepository.class);
        when(records.findById(5)).thenReturn(Optional.of(record));
        return new MedicalRecordService(records, prescriptions, ownershipJdbc(ownerId));
    }

    private static JdbcTemplate ownershipJdbc(int ownerId) {
        var jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), ArgumentMatchers.<ResultSetExtractor<Integer>>any(), eq("patient"))).thenReturn(42);
        when(jdbc.queryForMap(anyString(), eq(88))).thenReturn(Map.of("id_user", ownerId, "id_doctor", 63));
        return jdbc;
    }

    private static MedicalRecord record() {
        MedicalRecord value = new MedicalRecord();
        value.setAppointmentId(88);
        return value;
    }

    private static UsernamePasswordAuthenticationToken authentication(String username, String role) {
        return new UsernamePasswordAuthenticationToken(username, "", List.of(new SimpleGrantedAuthority(role)));
    }
}
