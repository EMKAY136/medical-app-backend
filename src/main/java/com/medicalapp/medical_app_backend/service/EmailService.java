package com.medicalapp.medical_app_backend.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    @Value("${spring.mail.username:qualitestmedical@gmail.com}")
    private String gmailUsername;

    @Autowired
    private JavaMailSender javaMailSender;

    // ─── Main send method ───────────────────────────────────────────────────────

    public void sendEmail(String toEmail, String subject, String htmlContent) {
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(gmailUsername);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            javaMailSender.send(message);
            log.info("=== EMAIL SENT VIA GMAIL to: {} ===", toEmail);
        } catch (Exception e) {
            log.error("Gmail error sending to {}: {}", toEmail, e.getMessage());
            throw new RuntimeException("Failed to send email via Gmail", e);
        }
    }

    // ─── Email Templates ────────────────────────────────────────────────────────

    public void sendVerificationEmail(String toEmail, String code) {
        String subject = "Email Verification - Medical App";
        String html = """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px;">
                    <h2 style="color: #2c7be5;">Email Verification</h2>
                    <p>Thank you for registering. Your verification code is:</p>
                    <div style="background: #f4f4f4; padding: 20px; text-align: center; border-radius: 8px; margin: 20px 0;">
                        <h1 style="color: #2c7be5; letter-spacing: 12px; margin: 0;">%s</h1>
                    </div>
                    <p>This code expires in <strong>10 minutes</strong>.</p>
                    <p style="color: #666; font-size: 12px;">
                        If you did not request this, please ignore this email.
                    </p>
                </div>
                """.formatted(code);
        sendEmail(toEmail, subject, html);
    }

    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        String subject = "Password Reset - Medical App";
        String html = """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px;">
                    <h2 style="color: #2c7be5;">Password Reset Request</h2>
                    <p>Click the button below to reset your password:</p>
                    <div style="text-align: center; margin: 30px 0;">
                        <a href="%s" style="background-color: #2c7be5; color: white;
                            padding: 14px 28px; text-decoration: none;
                            border-radius: 6px; font-size: 16px;">
                            Reset Password
                        </a>
                    </div>
                    <p>This link expires in <strong>1 hour</strong>.</p>
                    <p style="color: #666; font-size: 12px;">
                        If you did not request this, please ignore this email.
                    </p>
                </div>
                """.formatted(resetLink);
        sendEmail(toEmail, subject, html);
    }

    public void sendTestResultNotification(String toEmail, String patientName) {
        String subject = "Test Results Available - Medical App";
        String html = """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px;">
                    <h2 style="color: #2c7be5;">Your Test Results Are Ready</h2>
                    <p>Dear <strong>%s</strong>,</p>
                    <p>Your test results are now available. Please log in to your account to view them.</p>
                    <p style="color: #e63757; font-size: 13px;">
                        ⚠️ For medical emergencies, please contact your healthcare provider immediately.
                    </p>
                    <p style="color: #666; font-size: 12px;">
                        This is an automated message, please do not reply to this email.
                    </p>
                </div>
                """.formatted(patientName);
        sendEmail(toEmail, subject, html);
    }

    public void sendAppointmentConfirmation(String toEmail, String patientName, String appointmentDate) {
        String subject = "Appointment Confirmed - Medical App";
        String html = """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px;">
                    <h2 style="color: #2c7be5;">Appointment Confirmed</h2>
                    <p>Dear <strong>%s</strong>,</p>
                    <p>Your appointment has been confirmed for:</p>
                    <div style="background: #f4f4f4; padding: 15px; border-radius: 8px; margin: 20px 0;">
                        <strong>📅 %s</strong>
                    </div>
                    <p>Please arrive 10 minutes early.</p>
                    <p style="color: #666; font-size: 12px;">
                        This is an automated message, please do not reply to this email.
                    </p>
                </div>
                """.formatted(patientName, appointmentDate);
        sendEmail(toEmail, subject, html);
    }

    public void sendWelcomeEmail(String toEmail, String patientName) {
        String subject = "Welcome to Medical App";
        String html = """
                <div style="font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; padding: 20px;">
                    <h2 style="color: #2c7be5;">Welcome to Medical App 👋</h2>
                    <p>Dear <strong>%s</strong>,</p>
                    <p>Your account has been successfully created. You can now:</p>
                    <ul>
                        <li>View your medical records</li>
                        <li>Book appointments</li>
                        <li>Receive test results</li>
                        <li>Contact support</li>
                    </ul>
                    <p style="color: #666; font-size: 12px;">
                        If you have any issues, contact us during business hours (9AM - 6PM).
                    </p>
                </div>
                """.formatted(patientName);
        sendEmail(toEmail, subject, html);
    }
}