/**
 *
 */
package one.tracking.framework.integration;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.io.IOException;
import java.util.Optional;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import one.tracking.framework.SurveyManagementApplication;
import one.tracking.framework.dto.ParticipantInvitationDto;
import one.tracking.framework.dto.TokenResponseDto;
import one.tracking.framework.dto.VerificationDto;
import one.tracking.framework.entity.Verification;
import one.tracking.framework.repo.VerificationRepository;
import one.tracking.framework.service.SendGridService;
import one.tracking.framework.support.JWTHelper;

/**
 * @author Marko Voß
 *
 */
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application-it.properties")
@Import(ITConfiguration.class)
@RunWith(SpringRunner.class)
// @SpringBootTest(classes = SurveyManagementApplication.class, webEnvironment =
// SpringBootTest.WebEnvironment.DEFINED_PORT)
@SpringBootTest(classes = SurveyManagementApplication.class)
@DirtiesContext
@ActiveProfiles("dev")
public class AuthControllerIT {

  // private static final Logger LOG = LoggerFactory.getLogger(AuthControllerIT.class);

  private static final String ENDPOINT_AUTH = "/auth";
  private static final String ENDPOINT_VERIFY = ENDPOINT_AUTH + "/verify";

  private static final String ENDPOINT_MANAGE = "/manage";
  private static final String ENDPOINT_INVITE = ENDPOINT_MANAGE + "/invite";

  @MockBean
  private SendGridService sendGridService;

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private VerificationRepository verificationRepository;

  @Autowired
  private ObjectMapper mapper;


  @Autowired
  private JWTHelper jwtHelper;

  @Before
  public void before() throws IOException {
    Mockito.when(this.sendGridService.sendHTML(anyString(), anyString(), anyString())).thenReturn(true);
    Mockito.when(this.sendGridService.sendText(anyString(), anyString(), anyString())).thenReturn(true);
  }

  @Test
  public void test() throws Exception {

    final String email = "foo@example.com";

    /*
     * Test POST /register
     */

    this.mockMvc.perform(MockMvcRequestBuilders.post(ENDPOINT_INVITE)
        .with(csrf())
        .content(this.mapper.writeValueAsBytes(ParticipantInvitationDto.builder()
            .email(email)
            .build()))
        .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());

    this.mockMvc.perform(MockMvcRequestBuilders.post(ENDPOINT_INVITE)
        .with(csrf())
        .with(httpBasic("admin", "admin"))
        .content(this.mapper.writeValueAsBytes(ParticipantInvitationDto.builder()
            .email(email)
            .build()))
        .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());

    final Optional<Verification> verificationOp = this.verificationRepository.findByEmail(email);

    assertThat(verificationOp, is(not(nullValue())));
    assertThat(verificationOp.isPresent(), is(true));
    assertThat(verificationOp.get().getEmail(), is(equalTo(email)));
    assertThat(verificationOp.get().getHash(), is(not(nullValue())));
    assertThat(verificationOp.get().getHash().length(), is(256));

    final String verificationToken = verificationOp.get().getHash();

    /*
     * TEST GET /verify
     */

    this.mockMvc.perform(MockMvcRequestBuilders.get(ENDPOINT_VERIFY)
        .with(csrf()))
        .andExpect(status().isBadRequest());

    MvcResult result = this.mockMvc.perform(MockMvcRequestBuilders.get(ENDPOINT_VERIFY)
        .with(csrf())
        .param("token", verificationToken))
        .andExpect(status().isOk())
        .andReturn();

    assertThat(result.getResponse().getContentType(), startsWith(MediaType.TEXT_HTML_VALUE));
    assertThat(result.getResponse().getContentLength(), greaterThan(0));

    /*
     * Test POST /verify
     */

    this.mockMvc.perform(MockMvcRequestBuilders.post(ENDPOINT_VERIFY)
        .with(csrf())
        .content(this.mapper.writeValueAsBytes(VerificationDto.builder()
            .verificationToken(verificationToken)
            .build()))
        .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());

    result = this.mockMvc.perform(MockMvcRequestBuilders.post(ENDPOINT_VERIFY)
        .with(csrf())
        .with(httpBasic("client", "client"))
        .content(this.mapper.writeValueAsBytes(VerificationDto.builder()
            .verificationToken(verificationToken)
            .build()))
        .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andReturn();

    final TokenResponseDto tokenResponse =
        this.mapper.readValue(result.getResponse().getContentAsByteArray(), TokenResponseDto.class);

    assertThat(tokenResponse, is(not(nullValue())));
    assertThat(tokenResponse.getToken(), is(not(nullValue())));

    final String userToken = tokenResponse.getToken();

    final Claims claims = this.jwtHelper.decodeJWT(userToken);

    assertThat(claims, is(not(nullValue())));
    assertThat(claims.getSubject(), is(not(nullValue())));
    assertThat(claims.getSubject().length(), is(36));
  }
}
