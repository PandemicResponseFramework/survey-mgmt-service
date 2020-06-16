/**
 *
 */
package one.tracking.framework.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationEntryPoint;

/**
 * @author Marko Voß
 *
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Value("${app.security.role.admin}")
  private String roleAdmin;

  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Configuration
  @Order(0)
  public class SecurityConfigClient extends WebSecurityConfigurerAdapter {

    @Value("${app.security.verify.client-id}")
    private String username;

    @Value("${app.security.verify.client-secret}")
    private String password;

    @Bean
    public AuthenticationEntryPoint authenticationEntryPointClient() {
      final BasicAuthenticationEntryPoint entryPoint = new BasicAuthenticationEntryPoint();
      entryPoint.setRealmName("client");
      return entryPoint;
    }

    @Autowired
    public void configureGlobal(final AuthenticationManagerBuilder auth) throws Exception {
      auth.inMemoryAuthentication()
          .withUser(this.username)
          .password(passwordEncoder().encode(this.password))
          .authorities("ROLE_CLIENT");
    }

    @Override
    protected void configure(final HttpSecurity http) throws Exception {

      http.antMatcher("/auth/verify")
          .csrf().disable()
          .authorizeRequests()
          .antMatchers(HttpMethod.POST, "/auth/verify").authenticated()
          .antMatchers(HttpMethod.GET, "/auth/verify").permitAll()
          .and()
          .httpBasic()
          .authenticationEntryPoint(authenticationEntryPointClient())
          .and()
          .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);

    }
  }

  /**
   *
   * TODO: Implement IT based on mocking {@link OAuth2UserService} instead of switching to HTTP BASIC.
   *
   */
  @Configuration
  @Profile("dev")
  @Order(1)
  public class SecurityConfigDev extends WebSecurityConfigurerAdapter {

    @Value("${app.security.dev.user}")
    private String username;

    @Value("${app.security.dev.password}")
    private String password;

    @Bean
    public AuthenticationEntryPoint authenticationEntryPointDev() {
      final BasicAuthenticationEntryPoint entryPoint = new BasicAuthenticationEntryPoint();
      entryPoint.setRealmName("dev");
      return entryPoint;
    }

    @Autowired
    public void configureGlobal(final AuthenticationManagerBuilder auth) throws Exception {
      auth.inMemoryAuthentication()
          .withUser(this.username)
          .password(passwordEncoder().encode(this.password))
          .authorities(SecurityConfig.this.roleAdmin);
    }

    @Override
    protected void configure(final HttpSecurity http) throws Exception {

      http.antMatcher("/manage/**").csrf().disable()
          .authorizeRequests()
          .anyRequest().hasAnyAuthority(SecurityConfig.this.roleAdmin)
          .and()
          .httpBasic()
          .authenticationEntryPoint(authenticationEntryPointDev())
          .and()
          .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS);
    }
  }

  /**
   *
   * TODO: Implement IT based on mocking {@link OAuth2UserService} instead of switching to HTTP BASIC.
   *
   */
  @Configuration
  @Profile("!dev")
  @Order(1)
  public class SecurityConfigProd extends WebSecurityConfigurerAdapter {

    @Autowired
    private OAuth2UserService<OidcUserRequest, OidcUser> oidcUserService;


    @Override
    protected void configure(final HttpSecurity http) throws Exception {

      http.authorizeRequests()
          .anyRequest().hasAnyAuthority(SecurityConfig.this.roleAdmin)
          .and()
          .oauth2Login()
          .userInfoEndpoint()
          .oidcUserService(this.oidcUserService);
    }
  }
}
