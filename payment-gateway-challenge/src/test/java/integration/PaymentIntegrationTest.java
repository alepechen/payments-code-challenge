package integration;

import com.checkout.payment.gateway.PaymentGatewayApplication;
import com.checkout.payment.gateway.enums.PaymentStatus;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
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
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = PaymentGatewayApplication.class,webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
class PaymentIntegrationTest {

  // Card numbers that drive bank simulator behaviour:
  // ends in odd  (1,3,5,7,9) → 200 authorized
  // ends in even (2,4,6,8)   → 200 declined
  // ends in 0                → 503 Service Unavailable
  private static final String CARD_AUTHORIZED = "4111111111111111"; // ends in 1 → authorized
  private static final String CARD_DECLINED   = "4111111111111112"; // ends in 2 → declined
  private static final String CARD_ERROR      = "4111111111111110"; // ends in 0 → 503

  @RegisterExtension
  static WireMockExtension wireMock = WireMockExtension.newInstance()
      .options(wireMockConfig().dynamicPort())
      .build();

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("bank.base-url", wireMock::baseUrl);
  }

  @Autowired
  private MockMvc mvc;

  @Autowired
  private CircuitBreakerRegistry circuitBreakerRegistry;

  @BeforeEach
  void setUp() {
    wireMock.resetAll();
    circuitBreakerRegistry.circuitBreaker("bankCircuit").reset();
  }

  // ── Successful authorization ──────────────────────────────────────────────

  @Test
  void whenBankAuthorizesPayment_thenResponseIsAuthorizedAndStored() throws Exception {
    wireMock.stubFor(post(urlEqualTo("/payments"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                                { "authorized": true, "authorization_code": "AUTH123" }
                                """)));

    mvc.perform(MockMvcRequestBuilders.post("/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .content(paymentRequest(CARD_AUTHORIZED)))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(PaymentStatus.AUTHORIZED.getName()))
        .andExpect(jsonPath("$.id").isNotEmpty());

    wireMock.verify(1, postRequestedFor(urlEqualTo("/payments")));
  }

  // ── Declined payment ──────────────────────────────────────────────────────

  @Test
  void whenBankDeclinesPayment_thenResponseIsDeclined() throws Exception {
    wireMock.stubFor(post(urlEqualTo("/payments"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                                { "authorized": false, "authorization_code": null }
                                """)));

    mvc.perform(MockMvcRequestBuilders.post("/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .content(paymentRequest(CARD_DECLINED)))
        .andDo(print())
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
                                { "authorized": true, "authorization_code": "AUTH_IDEM" }
                                """)));

    String idempotencyKey = UUID.randomUUID().toString();
    String body = paymentRequest(CARD_AUTHORIZED);

    mvc.perform(MockMvcRequestBuilders.post("/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Idempotency-Key", idempotencyKey)
            .content(body))
        .andExpect(status().isOk());

    mvc.perform(MockMvcRequestBuilders.post("/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Idempotency-Key", idempotencyKey)
            .content(body))
        .andExpect(status().isOk());

    // Bank must only be hit once despite two HTTP calls
    wireMock.verify(1, postRequestedFor(urlEqualTo("/payments")));
  }

  // ── Bank errors ───────────────────────────────────────────────────────────

  @Test
  void whenBankReturns400_thenPaymentGatewayReturnsBadRequest() throws Exception {
    wireMock.stubFor(post(urlEqualTo("/payments"))
        .willReturn(aResponse()
            .withStatus(400)
            .withBody("missing required fields")));

    mvc.perform(MockMvcRequestBuilders.post("/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .content(paymentRequest(CARD_AUTHORIZED)))
        .andDo(print())
        .andExpect(status().isBadRequest());

    // Must not retry on 400
    wireMock.verify(1, postRequestedFor(urlEqualTo("/payments")));
  }

  @Test
  void whenBankReturns503_thenRetryOccursAndEventuallyReturnsServiceUnavailable() throws Exception {
    wireMock.stubFor(post(urlEqualTo("/payments"))
        .willReturn(aResponse().withStatus(503)));

    mvc.perform(MockMvcRequestBuilders.post("/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .content(paymentRequest(CARD_ERROR)))
        .andDo(print())
        .andExpect(status().isServiceUnavailable());

    // maxAttempts=3 in application.properties
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
                                { "authorized": true, "authorization_code": "AUTH_RETRY" }
                                """)));

    mvc.perform(MockMvcRequestBuilders.post("/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Idempotency-Key", UUID.randomUUID().toString())
            .content(paymentRequest(CARD_AUTHORIZED)))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(PaymentStatus.AUTHORIZED.getName()));

    wireMock.verify(2, postRequestedFor(urlEqualTo("/payments")));
  }

  // ── GET payment ───────────────────────────────────────────────────────────

  @Test
  void whenPaymentExistsAndIsRetrievedById_thenCorrectPaymentIsReturned() throws Exception {
    wireMock.stubFor(post(urlEqualTo("/payments"))
        .willReturn(aResponse()
            .withStatus(200)
            .withHeader("Content-Type", "application/json")
            .withBody("""
                                { "authorized": true, "authorization_code": "AUTH_GET" }
                                """)));

    String idempotencyKey = UUID.randomUUID().toString();

    String postResponse = mvc.perform(MockMvcRequestBuilders.post("/payment")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Idempotency-Key", idempotencyKey)
            .content(paymentRequest(CARD_AUTHORIZED)))
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();

    String id = com.jayway.jsonpath.JsonPath.read(postResponse, "$.id");

    mvc.perform(MockMvcRequestBuilders.get("/payment/" + id))
        .andDo(print())
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value(PaymentStatus.AUTHORIZED.getName()))
        .andExpect(jsonPath("$.currency").value("USD"))
        .andExpect(jsonPath("$.amount").value(100));
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private String paymentRequest(String cardNumber) {
    return """
                {
                  "card_number": "%s",
                  "expiry_month": 12,
                  "expiry_year": 2027,
                  "currency": "USD",
                  "amount": 100,
                  "cvv": "123"
                }
                """.formatted(cardNumber);
  }
}