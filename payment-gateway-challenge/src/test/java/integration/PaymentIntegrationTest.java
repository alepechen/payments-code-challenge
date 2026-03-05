package integration;

import com.checkout.payment.gateway.enums.PaymentStatus;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class PaymentIntegrationTest {

  @RegisterExtension
  static WireMockExtension wireMock = WireMockExtension.newInstance()
      .options(wireMockConfig().dynamicPort())
      .build();

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    // Point the bank client at WireMock instead of localhost:8080
    registry.add("bank.base-url", wireMock::baseUrl);
  }

  @Autowired
  private MockMvc mvc;

  @BeforeEach
  void resetWireMock() {
    wireMock.resetAll();
  }

  // ── Successful authorization ───────────────────────────────────────────────

  @Test
  void whenBankAuthorizesPayment_thenResponseIsAuthorizedAndStored() throws Exception {
    wireMock.stubFor(post(urlEqualTo("/payments"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                                { "authorization_code": "AUTH123", "status": true }
                                """)));

    mvc.perform(MockMvcRequestBuilders.post("/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .content(validPaymentRequest()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(PaymentStatus.AUTHORIZED.getName()))
        .andExpect(jsonPath("$.id").isNotEmpty());
  }

  @Test
  void whenBankDeclinesPayment_thenResponseIsDeclined() throws Exception {
    wireMock.stubFor(post(urlEqualTo("/payments"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                                { "authorization_code": null, "status": false }
                                """)));

    mvc.perform(MockMvcRequestBuilders.post("/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .content(validPaymentRequest()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(PaymentStatus.DECLINED.getName()));
  }

  // ── Idempotency ───────────────────────────────────────────────────────────

  @Test
  void whenSameIdempotencyKeyIsUsedTwice_thenBankIsOnlyCalledOnce() throws Exception {
    wireMock.stubFor(post(urlEqualTo("/payments"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                                { "authorization_code": "AUTH_IDEM", "status": true }
                                """)));

    String idempotencyKey = UUID.randomUUID().toString();
    String body = validPaymentRequest();

    mvc.perform(MockMvcRequestBuilders.post("/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Idempotency-Key", idempotencyKey)
            .content(body))
        .andExpect(status().isOk());

    mvc.perform(MockMvcRequestBuilders.post("/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Idempotency-Key", idempotencyKey)
            .content(body))
        .andExpect(status().isOk());

    // Bank must only be hit once despite two HTTP calls
    wireMock.verify(1, postRequestedFor(urlEqualTo("/payments")));
  }

  // ── Bank errors ───────────────────────────────────────────────────────────

  @Test
  void whenBankReturns400_thenPaymentGatewayReturnsUnprocessableEntity() throws Exception {
    wireMock.stubFor(post(urlEqualTo("/payments"))
        .willReturn(aResponse()
            .withStatus(400)
            .withBody("missing required fields")));

    mvc.perform(MockMvcRequestBuilders.post("/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .content(validPaymentRequest()))
        .andExpect(status().isUnprocessableEntity());
  }

  @Test
  void whenBankReturns503_thenRetryOccursAndEventuallyReturnsServiceUnavailable() throws Exception {
    wireMock.stubFor(post(urlEqualTo("/payments"))
        .willReturn(aResponse().withStatus(503)));

    mvc.perform(MockMvcRequestBuilders.post("/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .content(validPaymentRequest()))
        .andExpect(status().isServiceUnavailable());

    // Should have retried 3 times total
    wireMock.verify(3, postRequestedFor(urlEqualTo("/payments")));
  }

  @Test
  void whenBankReturns503ThenRecovers_thenPaymentSucceeds() throws Exception {
    wireMock.stubFor(post(urlEqualTo("/payments"))
        .inScenario("retry-then-succeed")
        .whenScenarioStateIs("Started")
        .willReturn(aResponse().withStatus(503))
        .willSetStateTo("recovered"));

    wireMock.stubFor(post(urlEqualTo("/payments"))
        .inScenario("retry-then-succeed")
        .whenScenarioStateIs("recovered")
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                                { "authorization_code": "AUTH_RETRY", "status": "authorized" }
                                """)));

    mvc.perform(MockMvcRequestBuilders.post("/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .content(validPaymentRequest()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(PaymentStatus.AUTHORIZED.getName()));
  }

  // ── Circuit Breaker ───────────────────────────────────────────────────────

  @Test
  void whenCircuitBreakerOpens_thenSubsequentCallsFailFastWithoutHittingBank() throws Exception {
    // Return 503 enough times to open the circuit
    wireMock.stubFor(post(urlEqualTo("/payments"))
        .willReturn(aResponse().withStatus(503)));

    String payload = validPaymentRequest();

    // Trip the circuit (10 calls × 100% failure > 50% threshold)
    for (int i = 0; i < 10; i++) {
      mvc.perform(MockMvcRequestBuilders.post("/payments")
              .contentType(MediaType.APPLICATION_JSON)
              .header("Idempotency-Key", UUID.randomUUID().toString())
              .content(payload))
          .andExpect(status().isServiceUnavailable());
    }

    wireMock.resetAll(); // clear stubs — bank would 404 if hit

    // This call should be rejected by the open circuit without reaching WireMock
    mvc.perform(MockMvcRequestBuilders.post("/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .content(payload))
        .andExpect(status().isServiceUnavailable());

    wireMock.verify(0, postRequestedFor(urlEqualTo("/payments")));
  }

  // ── GET payment ───────────────────────────────────────────────────────────

  @Test
  void whenPaymentExistsAndIsRetrievedById_thenCorrectPaymentIsReturned() throws Exception {
    wireMock.stubFor(post(urlEqualTo("/payments"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                                { "authorization_code": "AUTH_GET", "status": "authorized" }
                                """)));

    String idempotencyKey = UUID.randomUUID().toString();

    String postResponse = mvc.perform(MockMvcRequestBuilders.post("/payments")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Idempotency-Key", idempotencyKey)
            .content(validPaymentRequest()))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();

    // Extract ID from response (basic parse — use JsonPath or ObjectMapper in real project)
    String id = com.jayway.jsonpath.JsonPath.read(postResponse, "$.id");

    mvc.perform(MockMvcRequestBuilders.get("/payment/" + id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(PaymentStatus.AUTHORIZED.getName()))
        .andExpect(jsonPath("$.currency").value("USD"));
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private String validPaymentRequest() {
    return """
                {
                  "amount": 100,
                  "currency": "USD",
                  "card_number": "4111111111111111",
                  "expiry_month": 12,
                  "expiry_year": 2027,
                  "cvv": "123"
                }
                """;
  }
}
