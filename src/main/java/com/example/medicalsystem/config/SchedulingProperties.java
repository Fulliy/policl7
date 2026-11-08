package com.example.medicalsystem.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.scheduling")
public class SchedulingProperties {
    private int minNoticeMinutes = 60;
    private int maxAdvanceDays = 30;
    private int maxActivePerDoctor = 3;
    private int maxActiveTotal = 5;
    private int cancellationDeadlineHours = 2;
    private int noShowLookbackDays = 90;
    private int noShowLimit = 3;
    private int defaultVisitDurationMinutes = 30;
    private int nearestDateSearchDays = 90;
    private int holdDurationMinutes = 10;
    private int orviAdvanceDays = 7;
    private int bookingPreviewDays = 30;

    public int getMinNoticeMinutes() { return minNoticeMinutes; }
    public void setMinNoticeMinutes(int value) { minNoticeMinutes = value; }
    public int getMaxAdvanceDays() { return maxAdvanceDays; }
    public void setMaxAdvanceDays(int value) { maxAdvanceDays = value; }
    public int getMaxActivePerDoctor() { return maxActivePerDoctor; }
    public void setMaxActivePerDoctor(int value) { maxActivePerDoctor = value; }
    public int getMaxActiveTotal() { return maxActiveTotal; }
    public void setMaxActiveTotal(int value) { maxActiveTotal = value; }
    public int getCancellationDeadlineHours() { return cancellationDeadlineHours; }
    public void setCancellationDeadlineHours(int value) { cancellationDeadlineHours = value; }
    public int getNoShowLookbackDays() { return noShowLookbackDays; }
    public void setNoShowLookbackDays(int value) { noShowLookbackDays = value; }
    public int getNoShowLimit() { return noShowLimit; }
    public void setNoShowLimit(int value) { noShowLimit = value; }
    public int getDefaultVisitDurationMinutes() { return defaultVisitDurationMinutes; }
    public void setDefaultVisitDurationMinutes(int value) { defaultVisitDurationMinutes = value; }
    public int getNearestDateSearchDays() { return nearestDateSearchDays; }
    public void setNearestDateSearchDays(int value) { nearestDateSearchDays = value; }
    public int getHoldDurationMinutes() { return holdDurationMinutes; }
    public void setHoldDurationMinutes(int value) { holdDurationMinutes = value; }
    public int getOrviAdvanceDays() { return orviAdvanceDays; }
    public void setOrviAdvanceDays(int value) { orviAdvanceDays = value; }
    public int getBookingPreviewDays() { return bookingPreviewDays; }
    public void setBookingPreviewDays(int value) { bookingPreviewDays = value; }
}
