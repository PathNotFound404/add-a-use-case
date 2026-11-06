package team.projectpulse.security;

import jakarta.persistence.EntityManager;
import org.json.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import team.projectpulse.AbstractIntegrationTest;
import team.projectpulse.user.PeerEvaluationUser;
import team.projectpulse.system.StatusCode;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FR-SEC-active-account: a deactivated account is refused on its next request, not only at its next login, even
 * though the JWT it was issued before deactivation is still within its two hours (UC-STU-deactivate-student step 6,
 * and UC-INS-deactivate-instructor through the same shared user table).
 */
@DisplayName("Integration tests for the per-request active-account check")
class ActiveAccountIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    EntityManager entityManager;

    @Value("${api.endpoint.base-url}")
    String baseUrl;


    private String login(String email) throws Exception {
        MvcResult mvcResult = this.mockMvc.perform(post(this.baseUrl + "/users/login").with(httpBasic(email, "123456"))).andReturn();
        JSONObject json = new JSONObject(mvcResult.getResponse().getContentAsString());
        return "Bearer " + json.getJSONObject("data").getString("token");
    }

    private void deactivate(String email) {
        PeerEvaluationUser user = this.entityManager
                .createQuery("select u from PeerEvaluationUser u where u.email = :email", PeerEvaluationUser.class)
                .setParameter("email", email)
                .getSingleResult();
        user.setEnabled(false);
        this.entityManager.flush();
    }

    @Test
    @DisplayName("An active account's token keeps working")
    void activeStudentTokenWorks() throws Exception {
        String johnToken = login("j.smith@abc.edu");

        this.mockMvc.perform(get(this.baseUrl + "/students/4").accept(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, johnToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flag").value(true));
    }

    @Test
    @DisplayName("A student's token issued before deactivation is refused afterwards, and so is her next login")
    void deactivatedStudentIsRefused() throws Exception {
        String johnToken = login("j.smith@abc.edu");
        deactivate("j.smith@abc.edu");

        this.mockMvc.perform(get(this.baseUrl + "/students/4").accept(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, johnToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.flag").value(false))
                .andExpect(jsonPath("$.code").value(StatusCode.UNAUTHORIZED));

        this.mockMvc.perform(post(this.baseUrl + "/users/login").with(httpBasic("j.smith@abc.edu", "123456")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.flag").value(false))
                .andExpect(jsonPath("$.code").value(StatusCode.UNAUTHORIZED));
    }

    @Test
    @DisplayName("A deactivated instructor's previously issued token is refused")
    void deactivatedInstructorIsRefused() throws Exception {
        String billToken = login("b.gates@abc.edu");
        deactivate("b.gates@abc.edu");

        this.mockMvc.perform(get(this.baseUrl + "/students/4").accept(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, billToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(StatusCode.UNAUTHORIZED));
    }

}
