package team.projectpulse.student;

import jakarta.persistence.EntityManager;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import team.projectpulse.AbstractIntegrationTest;
import team.projectpulse.instructor.Instructor;
import team.projectpulse.section.Section;
import team.projectpulse.system.StatusCode;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * UC-STU-deactivate-student through {@code PUT /students/{studentId}}: who may deactivate (BR-student-lifecycle),
 * what is kept, an update that carries no {@code enabled}, and 6a. Seed: John (id 4) and Eric (id 5) are on Team1
 * in course section 2 of course 1, which Bingyang owns; Bill is assigned to course sections 1 and 2; Tim owns
 * course 3 only.
 */
@DisplayName("Integration tests for deactivating a student")
class StudentDeactivationIntegrationTest extends AbstractIntegrationTest {

    private static final int JOHN = 4;
    private static final int ERIC = 5;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JsonMapper jsonMapper;

    @Autowired
    EntityManager entityManager;

    @Value("${api.endpoint.base-url}")
    String baseUrl;

    String adminBingyangToken;
    String instructorBillToken;
    String adminTimToken;
    String studentJohnToken;
    String studentEricToken;


    @BeforeEach
    void setUp() throws Exception {
        this.adminBingyangToken = login("b.wei@abc.edu");
        this.instructorBillToken = login("b.gates@abc.edu");
        this.adminTimToken = login("t.cook@abc.edu");
        this.studentJohnToken = login("j.smith@abc.edu");
        this.studentEricToken = login("e.hudson@abc.edu");
    }

    private String login(String email) throws Exception {
        MvcResult mvcResult = this.mockMvc.perform(post(this.baseUrl + "/users/login").with(httpBasic(email, "123456"))).andReturn();
        JSONObject json = new JSONObject(mvcResult.getResponse().getContentAsString());
        return "Bearer " + json.getJSONObject("data").getString("token");
    }

    /** The record as the details dialog loaded it, plus {@code enabled} when the action sends one. */
    private ResultActions putStudent(int studentId, String firstName, String lastName, String email, Boolean enabled, String token) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("username", email);
        body.put("firstName", firstName);
        body.put("lastName", lastName);
        body.put("email", email);
        if (enabled != null) {
            body.put("enabled", enabled);
        }
        return this.mockMvc.perform(put(this.baseUrl + "/students/" + studentId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(this.jsonMapper.writeValueAsString(body))
                .accept(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, token));
    }

    private ResultActions deactivateJohn(String token) throws Exception {
        return putStudent(JOHN, "John", "Smith", "j.smith@abc.edu", false, token);
    }

    private boolean isEnabled(int studentId) {
        this.entityManager.flush();
        this.entityManager.clear();
        return this.entityManager.find(Student.class, studentId).isEnabled();
    }

    private long count(String jpql) {
        return this.entityManager.createQuery(jpql, Long.class).setParameter("id", JOHN).getSingleResult();
    }

    private void unassignFromCourseSection2(String email) {
        Section section = this.entityManager.find(Section.class, 2);
        Instructor instructor = this.entityManager.createQuery("select i from Instructor i where i.email = :email", Instructor.class)
                .setParameter("email", email).getSingleResult();
        section.removeInstructor(instructor);
        this.entityManager.flush();
    }

    // Main success scenario

    @Test
    @DisplayName("Main flow: an instructor of her course section deactivates her; her course section, team, and work are kept")
    void courseSectionInstructorDeactivatesStudent() throws Exception {
        long activities = count("select count(a) from Activity a where a.student.id = :id");
        long evaluations = count("select count(e) from PeerEvaluation e where e.evaluator.id = :id or e.evaluatee.id = :id");
        assertThat(activities).isPositive(); // Otherwise "kept" below would hold vacuously
        assertThat(evaluations).isPositive();

        deactivateJohn(this.instructorBillToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flag").value(true))
                .andExpect(jsonPath("$.code").value(StatusCode.SUCCESS))
                .andExpect(jsonPath("$.data.firstName").value("John"))
                .andExpect(jsonPath("$.data.lastName").value("Smith"))
                .andExpect(jsonPath("$.data.enabled").value(false));

        assertThat(isEnabled(JOHN)).isFalse();
        Student john = this.entityManager.find(Student.class, JOHN);
        assertThat(john.getSection().getSectionId()).isEqualTo(2);
        assertThat(john.getTeam()).isNotNull();
        assertThat(john.getTeam().getTeamName()).isEqualTo("Team1");
        assertThat(count("select count(a) from Activity a where a.student.id = :id")).isEqualTo(activities);
        assertThat(count("select count(e) from PeerEvaluation e where e.evaluator.id = :id or e.evaluatee.id = :id")).isEqualTo(evaluations);
    }

    @Test
    @DisplayName("Main flow: the course admin who owns her course deactivates her without being assigned to her course section")
    void owningCourseAdminDeactivatesStudentWithoutBeingAssigned() throws Exception {
        unassignFromCourseSection2("b.wei@abc.edu");

        deactivateJohn(this.adminBingyangToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));

        assertThat(isEnabled(JOHN)).isFalse();
    }

    // Who may not (BR-student-lifecycle)

    @Test
    @DisplayName("An instructor who is not assigned to her course section is refused")
    void instructorOfAnotherCourseSectionIsForbidden() throws Exception {
        unassignFromCourseSection2("b.gates@abc.edu"); // Bill keeps course section 1, so he is an instructor elsewhere

        deactivateJohn(this.instructorBillToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.flag").value(false))
                .andExpect(jsonPath("$.code").value(StatusCode.FORBIDDEN));

        assertThat(isEnabled(JOHN)).isTrue();
    }

    @Test
    @DisplayName("The course admin of another course is refused")
    void courseAdminOfAnotherCourseIsForbidden() throws Exception {
        deactivateJohn(this.adminTimToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.flag").value(false))
                .andExpect(jsonPath("$.code").value(StatusCode.FORBIDDEN));

        assertThat(isEnabled(JOHN)).isTrue();
    }

    @Test
    @DisplayName("Another student, even a teammate, is refused")
    void anotherStudentIsForbidden() throws Exception {
        deactivateJohn(this.studentEricToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.flag").value(false))
                .andExpect(jsonPath("$.code").value(StatusCode.FORBIDDEN));

        assertThat(isEnabled(JOHN)).isTrue();
    }

    @Test
    @DisplayName("The student herself may save her profile, but enabled: false from her is ignored")
    void studentCannotDeactivateHerself() throws Exception {
        deactivateJohn(this.studentJohnToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flag").value(true))
                .andExpect(jsonPath("$.data.enabled").value(true));

        assertThat(isEnabled(JOHN)).isTrue();
    }

    // An update that carries no enabled

    @Test
    @DisplayName("A profile save without enabled leaves a deactivated student deactivated, and an active one active")
    void updateWithoutEnabledLeavesStatusUnchanged() throws Exception {
        this.entityManager.find(Student.class, JOHN).setEnabled(false);
        this.entityManager.flush();

        putStudent(JOHN, "John (edited)", "Smith", "j.smith@abc.edu", null, this.instructorBillToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstName").value("John (edited)"))
                .andExpect(jsonPath("$.data.enabled").value(false));
        putStudent(ERIC, "Eric (edited)", "Hudson", "e.hudson@abc.edu", null, this.instructorBillToken)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true));

        assertThat(isEnabled(JOHN)).isFalse();
        assertThat(isEnabled(ERIC)).isTrue();
    }

    // 6a

    @Test
    @DisplayName("6a: a student who no longer exists is refused at the route, and nothing is created")
    void studentThatNoLongerExistsIsForbidden() throws Exception {
        long students = this.entityManager.createQuery("select count(s) from Student s", Long.class).getSingleResult();

        putStudent(999999, "Ghost", "Student", "ghost@abc.edu", false, this.adminBingyangToken)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.flag").value(false))
                .andExpect(jsonPath("$.code").value(StatusCode.FORBIDDEN));

        assertThat(this.entityManager.createQuery("select count(s) from Student s", Long.class).getSingleResult()).isEqualTo(students);
        assertThat(this.entityManager.find(Student.class, 999999)).isNull();
    }

    @Test
    @DisplayName("Read-back: the deactivated status is what a later GET returns")
    void deactivatedStatusIsReadBack() throws Exception {
        deactivateJohn(this.instructorBillToken).andExpect(status().isOk());

        this.mockMvc.perform(get(this.baseUrl + "/students/" + JOHN).accept(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, this.instructorBillToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));
    }

}
