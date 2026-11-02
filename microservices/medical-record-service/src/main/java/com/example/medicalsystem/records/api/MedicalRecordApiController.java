package com.example.medicalsystem.records.api;

import com.example.medicalsystem.records.model.MedicalRecord;
import com.example.medicalsystem.records.service.MedicalRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/medical-records")
@Tag(name = "Медицинские записи")
public class MedicalRecordApiController {
    private final MedicalRecordService service;
    public MedicalRecordApiController(MedicalRecordService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "Список медицинских записей, доступных текущему пользователю")
    public List<RecordDto> list(Authentication auth) { return service.recordsVisibleTo(auth).stream().map(RecordDto::from).toList(); }

    @GetMapping("/{id}")
    @Operation(summary = "Медицинская запись с проверкой пациента или врача")
    public RecordDto get(@PathVariable Integer id, Authentication auth) { return RecordDto.from(service.getRecord(id, auth)); }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN','DOCTOR')")
    @Operation(summary = "Создать медицинскую запись")
    public RecordDto create(@Valid @RequestBody RecordRequest body, Authentication auth) {
        return RecordDto.from(service.createRecord(body.toRequest(), auth));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','DOCTOR')")
    @Operation(summary = "Изменить медицинскую запись")
    public RecordDto update(@PathVariable Integer id, @Valid @RequestBody RecordRequest body, Authentication auth) {
        return RecordDto.from(service.updateRecord(id, body.toRequest(), auth));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN','DOCTOR')")
    @Operation(summary = "Удалить медицинскую запись")
    public void delete(@PathVariable Integer id, Authentication auth) { service.deleteRecord(id, auth); }

    public record RecordRequest(@NotNull Integer appointmentId, String diagnosis, String treatment, String recommendations) {
        MedicalRecordService.RecordRequest toRequest() {
            return new MedicalRecordService.RecordRequest(appointmentId, diagnosis, treatment, recommendations);
        }
    }
    public record RecordDto(Integer id, String diagnosis, String treatment, String recommendations,
                            LocalDateTime createdDate, Integer appointmentId) {
        static RecordDto from(MedicalRecord value) {
            return new RecordDto(value.getIdRecord(), value.getDiagnosis(), value.getTreatment(),
                    value.getRecommendations(), value.getCreatedDate(), value.getAppointmentId());
        }
    }
}
