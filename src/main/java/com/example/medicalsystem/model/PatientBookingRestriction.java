package com.example.medicalsystem.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "patient_booking_restrictions")
public class PatientBookingRestriction {
    @Id @Column(name = "id_user") private Integer userId;
    @Column(nullable = false) private boolean blocked;
    @Column(name = "no_show_block_overridden", nullable = false) private boolean noShowBlockOverridden;
    @Column(length = 500) private String reason;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt = LocalDateTime.now();
    public Integer getUserId() { return userId; }
    public void setUserId(Integer value) { userId = value; }
    public boolean isBlocked() { return blocked; }
    public void setBlocked(boolean value) { blocked = value; }
    public boolean isNoShowBlockOverridden() { return noShowBlockOverridden; }
    public void setNoShowBlockOverridden(boolean value) { noShowBlockOverridden = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { reason = value; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { updatedAt = value; }
}
