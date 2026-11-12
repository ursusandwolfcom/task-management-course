package com.example.taskmanagement.support;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.mail.MailException;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/** Test double for SMTP: records SimpleMailMessage sends; no Mockito. */
public class RecordingMailSender implements JavaMailSender {
    private final List<SimpleMailMessage> messages = new CopyOnWriteArrayList<>();
    private volatile boolean failSends;

    @Override
    public void send(SimpleMailMessage... simpleMessages) throws MailException {
        for (SimpleMailMessage message : simpleMessages) {
            messages.add(copy(message));
        }
        if (failSends) {
            throw new MailSendException("SMTP unavailable");
        }
    }

    @Override
    public void send(SimpleMailMessage simpleMessage) throws MailException {
        send(new SimpleMailMessage[] {simpleMessage});
    }

    public List<SimpleMailMessage> messages() {
        return List.copyOf(messages);
    }

    public void clear() {
        messages.clear();
    }

    public void failSends(boolean fail) {
        this.failSends = fail;
    }

    private static SimpleMailMessage copy(SimpleMailMessage source) {
        var copy = new SimpleMailMessage();
        copy.setFrom(source.getFrom());
        copy.setTo(source.getTo());
        copy.setSubject(source.getSubject());
        copy.setText(source.getText());
        return copy;
    }

    @Override
    public MimeMessage createMimeMessage() {
        return new MimeMessage(Session.getInstance(new Properties()));
    }

    @Override
    public MimeMessage createMimeMessage(InputStream contentStream) {
        throw new UnsupportedOperationException("Mime messages are unused in course tests");
    }

    @Override
    public void send(MimeMessage mimeMessage) {
        throw new UnsupportedOperationException("Mime messages are unused in course tests");
    }

    @Override
    public void send(MimeMessage... mimeMessages) {
        throw new UnsupportedOperationException("Mime messages are unused in course tests");
    }
}
