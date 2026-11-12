package com.example.taskmanagement.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {
    @Mock
    private JavaMailSender sender;

    @Test
    void sendBuildsMessageAndSwallowsMailFailures() {
        var service = new EmailServiceImpl(sender, "noreply@example.test");
        service.send(new TaskEmail("user@example.test", "Subject", "Body"));
        var message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(message.capture());
        org.assertj.core.api.Assertions.assertThat(message.getValue().getFrom()).isEqualTo("noreply@example.test");
        org.assertj.core.api.Assertions.assertThat(message.getValue().getTo()).containsExactly("user@example.test");
        org.assertj.core.api.Assertions.assertThat(message.getValue().getSubject()).isEqualTo("Subject");

        doThrow(new MailSendException("down")).when(sender).send(any(SimpleMailMessage.class));
        service.send(new TaskEmail("user@example.test", "Subject", "Body"));
    }
}
