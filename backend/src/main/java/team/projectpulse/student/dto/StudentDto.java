package team.projectpulse.student.dto;

import jakarta.validation.constraints.NotEmpty;

public record StudentDto(Integer id,
                         @NotEmpty(message = "username is required.")
                         String username,
                         @NotEmpty(message = "first rubricName is required.")
                         String firstName,
                         @NotEmpty(message = "last rubricName is required.")
                         String lastName,
                         @NotEmpty(message = "email is required.")
                         String email,
                         Boolean enabled, // Optional on update: absent (null) leaves the account status unchanged
                         String roles,
                         Integer sectionId,
                         String sectionName,
                         Integer teamId,
                         String teamName) {
}
