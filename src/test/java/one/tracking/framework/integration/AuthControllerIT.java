/**
 *
 */
package one.tracking.framework.integration;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.io.IOException;
import java.util.List;
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
import org.springframework.http.HttpHeaders;
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
import one.tracking.framework.dto.DeviceTokenDto;
import one.tracking.framework.dto.ParticipantInvitationDto;
import one.tracking.framework.dto.TokenResponseDto;
import one.tracking.framework.dto.VerificationDto;
import one.tracking.framework.entity.DeviceToken;
import one.tracking.framework.entity.User;
import one.tracking.framework.entity.Verification;
import one.tracking.framework.repo.DeviceTokenRepository;
import one.tracking.framework.repo.UserRepository;
import one.tracking.framework.repo.VerificationRepository;
import one.tracking.framework.service.SendGridService;
import one.tracking.framework.support.JWTHelper;
import one.tracking.framework.support.ServiceUtility;

/**
 * @author Marko Voß
 *
 */
@AutoConfigureMockMvc
@TestPropertySource(locations = "classpath:application-it.properties")
@Import(ITConfiguration.class)
@RunWith(SpringRunner.class)
// @SpringBootTest(classes = SurveyApplication.class, webEnvironment =
// SpringBootTest.WebEnvironment.DEFINED_PORT)
@SpringBootTest(classes = SurveyManagementApplication.class)
@DirtiesContext
@ActiveProfiles("dev")
public class AuthControllerIT {

  // private static final Logger LOG = LoggerFactory.getLogger(AuthControllerIT.class);

  private static final String ENDPOINT_AUTH = "/auth";
  private static final String ENDPOINT_VERIFY = ENDPOINT_AUTH + "/verify";
  private static final String ENDPOINT_DEVICETOKEN = ENDPOINT_AUTH + "/devicetoken";

  private static final String ENDPOINT_MANAGE = "/manage";
  private static final String ENDPOINT_MANAGE_PARTICIPANT = ENDPOINT_MANAGE + "/participant";
  private static final String ENDPOINT_MANAGE_PARTICIPANT_INVITE = ENDPOINT_MANAGE_PARTICIPANT + "/invite";

  @MockBean
  private SendGridService sendGridService;

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private VerificationRepository verificationRepository;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private DeviceTokenRepository deviceTokenRepository;

  @Autowired
  private ObjectMapper mapper;

  @Autowired
  private ServiceUtility utility;

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

    this.mockMvc.perform(MockMvcRequestBuilders.post(ENDPOINT_MANAGE_PARTICIPANT_INVITE)
        .with(csrf())
        .content(this.mapper.writeValueAsBytes(ParticipantInvitationDto.builder()
            .email(email)
            .build()))
        .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());

    this.mockMvc.perform(MockMvcRequestBuilders.post(ENDPOINT_MANAGE_PARTICIPANT_INVITE)
        .with(csrf())
        .with(httpBasic("admin", "admin"))
        .content(this.mapper.writeValueAsBytes(ParticipantInvitationDto.builder()
            .email(email)
            .build()))
        .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());

    Optional<Verification> verificationOp = this.verificationRepository.findByEmail(email);

    assertThat(verificationOp, is(not(nullValue())));
    assertThat(verificationOp.isPresent(), is(true));
    assertThat(verificationOp.get().getEmail(), is(equalTo(email)));
    assertThat(verificationOp.get().getHash(), is(not(nullValue())));
    assertThat(verificationOp.get().getHash().length(), is(256));

    String verificationToken = verificationOp.get().getHash();

    /*
     * Test POST /verify
     */

    // Test without client credentials
    this.mockMvc.perform(MockMvcRequestBuilders.post(ENDPOINT_VERIFY)
        .with(csrf())
        .content(this.mapper.writeValueAsBytes(VerificationDto.builder()
            .verificationToken(verificationToken)
            .build()))
        .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isUnauthorized());

    // Test with admin credentials for management endpoints
    this.mockMvc.perform(MockMvcRequestBuilders.post(ENDPOINT_VERIFY)
        .with(csrf())
        .with(httpBasic("admin", "admin"))
        .content(this.mapper.writeValueAsBytes(VerificationDto.builder()
            .verificationToken(verificationToken)
            .build()))
        .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isForbidden());

    // Test ok
    MvcResult result = this.mockMvc.perform(MockMvcRequestBuilders.post(ENDPOINT_VERIFY)
        .with(csrf())
        .with(httpBasic("client", "client"))
        .content(this.mapper.writeValueAsBytes(VerificationDto.builder()
            .verificationToken(verificationToken)
            .build()))
        .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andReturn();

    TokenResponseDto tokenResponse =
        this.mapper.readValue(result.getResponse().getContentAsByteArray(), TokenResponseDto.class);

    assertThat(tokenResponse, is(not(nullValue())));
    assertThat(tokenResponse.getToken(), is(not(nullValue())));

    String userToken = tokenResponse.getToken();

    Claims claims = this.jwtHelper.decodeJWT(userToken);

    assertThat(claims, is(not(nullValue())));
    assertThat(claims.getSubject(), is(not(nullValue())));
    assertThat(claims.getSubject().length(), is(36));

    final String userId = claims.getSubject();

    /*
     * Test POST /devicetoken
     */

    final String deviceToken = this.utility.generateString(256);
    assertThat(deviceToken, is(not(nullValue())));
    assertThat(deviceToken.length(), is(256));

    this.mockMvc.perform(MockMvcRequestBuilders.post(ENDPOINT_DEVICETOKEN)
        .with(csrf())
        .content(this.mapper.writeValueAsBytes(DeviceTokenDto.builder()
            .token(deviceToken)
            .build()))
        .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isForbidden());

    this.mockMvc.perform(MockMvcRequestBuilders.post(ENDPOINT_DEVICETOKEN)
        .with(csrf())
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
        .content(this.mapper.writeValueAsBytes(DeviceTokenDto.builder()
            .token(deviceToken)
            .build()))
        .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());

    final Optional<User> userOp = this.userRepository.findById(userId);

    assertThat(userOp, is(not(nullValue())));
    assertThat(userOp.get().getId(), is(equalTo(userId)));

    List<DeviceToken> deviceTokens = this.deviceTokenRepository.findByUser(userOp.get());

    assertThat(deviceTokens, is(not(nullValue())));
    assertThat(deviceTokens.size(), is(1));
    assertThat(deviceTokens.get(0).getToken(), is(equalTo(deviceToken)));

    /*
     * Now register the same user again using a different email but the same device
     * ----------------------------------------------------------------------------
     */

    final String otherEmail = "bar@example.com";

    /*
     * Test POST /register
     */

    this.mockMvc.perform(MockMvcRequestBuilders.post(ENDPOINT_MANAGE_PARTICIPANT_INVITE)
        .with(csrf())
        .with(httpBasic("admin", "admin"))
        .content(this.mapper.writeValueAsBytes(ParticipantInvitationDto.builder()
            .email(otherEmail)
            .build()))
        .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());

    verificationOp = this.verificationRepository.findByEmail(otherEmail);

    assertThat(verificationOp, is(not(nullValue())));
    assertThat(verificationOp.isPresent(), is(true));
    assertThat(verificationOp.get().getEmail(), is(equalTo(otherEmail)));
    assertThat(verificationOp.get().getHash(), is(not(nullValue())));
    assertThat(verificationOp.get().getHash().length(), is(256));

    verificationToken = verificationOp.get().getHash();

    /*
     * Test POST /verify
     */

    result = this.mockMvc.perform(MockMvcRequestBuilders.post(ENDPOINT_VERIFY)
        .with(csrf())
        .with(httpBasic("client", "client"))
        .content(this.mapper.writeValueAsBytes(VerificationDto.builder()
            .verificationToken(verificationToken)
            .build()))
        .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk())
        .andReturn();

    tokenResponse = this.mapper.readValue(result.getResponse().getContentAsByteArray(), TokenResponseDto.class);

    assertThat(tokenResponse, is(not(nullValue())));
    assertThat(tokenResponse.getToken(), is(not(nullValue())));

    userToken = tokenResponse.getToken();

    claims = this.jwtHelper.decodeJWT(userToken);

    assertThat(claims, is(not(nullValue())));
    assertThat(claims.getSubject(), is(not(nullValue())));
    assertThat(claims.getSubject().length(), is(36));

    final String otherUserId = claims.getSubject();

    /*
     * Test POST /devicetoken
     */

    this.mockMvc.perform(MockMvcRequestBuilders.post(ENDPOINT_DEVICETOKEN)
        .with(csrf())
        .content(this.mapper.writeValueAsBytes(DeviceTokenDto.builder()
            .token(deviceToken)
            .build()))
        .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isForbidden());

    this.mockMvc.perform(MockMvcRequestBuilders.post(ENDPOINT_DEVICETOKEN)
        .with(csrf())
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
        .content(this.mapper.writeValueAsBytes(DeviceTokenDto.builder()
            .token(deviceToken)
            .build()))
        .contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isOk());

    final Optional<User> otherUserOp = this.userRepository.findById(otherUserId);

    assertThat(otherUserOp, is(not(nullValue())));
    assertThat(otherUserOp.get().getId(), is(equalTo(otherUserId)));

    deviceTokens = this.deviceTokenRepository.findByUser(otherUserOp.get());

    assertThat(deviceTokens, is(not(nullValue())));
    assertThat(deviceTokens.size(), is(1));
    assertThat(deviceTokens.get(0).getToken(), is(equalTo(deviceToken)));

    // Check if previous entry got deleted
    deviceTokens = this.deviceTokenRepository.findByUser(userOp.get());
    assertThat(deviceTokens, is(not(nullValue())));
    assertThat(deviceTokens.size(), is(0));
  }
}
