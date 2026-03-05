package service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.checkout.payment.gateway.PaymentGatewayApplication;
import com.checkout.payment.gateway.client.HttpBankClient;
import com.checkout.payment.gateway.exception.BankRequestException;
import com.checkout.payment.gateway.exception.BankServiceUnavailableException;
import com.checkout.payment.gateway.model.BankResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

@SpringBootTest(classes = PaymentGatewayApplication.class)
class HttpBankClientTest {

  @MockBean
  private RestTemplate restTemplate;

  @Autowired
  private HttpBankClient bankClient;

  @Autowired
  private CircuitBreakerRegistry circuitBreakerRegistry;

  @BeforeEach
  void resetCircuitBreaker() {
    circuitBreakerRegistry.circuitBreaker("bankCircuit")
        .reset();
  }

  @Test
  void whenBankReturnsSuccessfulResponse_thenBankResponseIsReturned() {
    BankResponse bankResponse = new BankResponse(true, "AUTH123");

    when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
        eq(BankResponse.class)))
        .thenReturn(ResponseEntity.ok(bankResponse));

    BankResponse result = bankClient.authorize(buildRequest());

    assertThat(result).isNotNull();
    assertThat(result.getAuthorizationCode()).isEqualTo("AUTH123");
    verify(restTemplate, times(1)).exchange(anyString(), eq(HttpMethod.POST), any(),
        eq(BankResponse.class));
  }

  // ── 400 Bad Request — no retry, no circuit breaker ───────────────────────

  @Test
  void whenBankReturns400_thenBankRequestExceptionIsThrownWithNoRetry() {
    when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
        eq(BankResponse.class)))
        .thenThrow(HttpClientErrorException.BadRequest.create(
            HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY, new byte[0], null));

    assertThatThrownBy(() -> bankClient.authorize(buildRequest()))
        .isInstanceOf(BankRequestException.class)
        .hasMessageContaining("Bank rejected request");

    // Must NOT retry on 400 — exactly 1 call
    verify(restTemplate, times(1)).exchange(anyString(), eq(HttpMethod.POST), any(),
        eq(BankResponse.class));
  }

  @Test
  void whenBankReturns400_thenCircuitBreakerRemainsClosedAfterMultipleCalls() {
    when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
        eq(BankResponse.class)))
        .thenThrow(HttpClientErrorException.BadRequest.create(
            HttpStatus.BAD_REQUEST, "Bad Request", HttpHeaders.EMPTY, new byte[0], null));

    // 400s should never count as failures toward the circuit breaker
    for (int i = 0; i < 4; i++) {
      try {
        bankClient.authorize(buildRequest());
      } catch (BankRequestException ignored) {
      }
    }

    CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("bankClient");
    assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
  }
  // ── 503 Service Unavailable — retried, trips circuit breaker ─────────────

  @Test
  void whenBankReturns503_thenBankServiceUnavailableExceptionIsThrown() {
    when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
        eq(BankResponse.class)))
        .thenThrow(HttpServerErrorException.ServiceUnavailable.create(
            HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", HttpHeaders.EMPTY, new byte[0],
            null));

    assertThatThrownBy(() -> bankClient.authorize(buildRequest()))
        .isInstanceOf(BankServiceUnavailableException.class);
  }

  @Test
  void whenBankReturns503_thenRequestIsRetried3Times() {
    when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
        eq(BankResponse.class)))
        .thenThrow(HttpServerErrorException.ServiceUnavailable.create(
            HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", HttpHeaders.EMPTY, new byte[0],
            null));

    try {
      bankClient.authorize(buildRequest());
    } catch (BankServiceUnavailableException ignored) {
    }

    // max-attempts: 3 in application.yml
    verify(restTemplate, times(3)).exchange(anyString(), eq(HttpMethod.POST), any(),
        eq(BankResponse.class));
  }

  @Test
  void whenBankReturns503ThenRecovers_thenSuccessIsReturnedOnRetry() {
    BankResponse bankResponse = new BankResponse(true, "AUTH456");

    when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
        eq(BankResponse.class)))
        .thenThrow(HttpServerErrorException.ServiceUnavailable.create(
            HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", HttpHeaders.EMPTY, new byte[0],
            null))
        .thenReturn(ResponseEntity.ok(bankResponse));

    BankResponse result = bankClient.authorize(buildRequest());

    assertThat(result.getAuthorizationCode()).isEqualTo("AUTH456");
    verify(restTemplate, times(2)).exchange(anyString(), eq(HttpMethod.POST), any(),
        eq(BankResponse.class));
  }

  // ── Circuit Breaker ───────────────────────────────────────────────────────

  @Test
  void whenFailureRateExceeded_thenCircuitBreakerOpens() {
    when(restTemplate.exchange(anyString(), eq(HttpMethod.POST), any(HttpEntity.class),
        eq(BankResponse.class)))
        .thenThrow(HttpServerErrorException.ServiceUnavailable.create(
            HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", HttpHeaders.EMPTY, new byte[0],
            null));

    CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("bankCircuit");

    // Read window size from the real config so the test doesn't duplicate magic numbers
    int slidingWindowSize = cb.getCircuitBreakerConfig().getSlidingWindowSize();
    for (int i = 0; i < slidingWindowSize; i++) {
      try {
        bankClient.authorize(buildRequest());
      } catch (Exception ignored) {
      }
    }

    assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);
  }

  @Test
  void whenCircuitIsOpen_thenCallsAreRejectedImmediatelyWithoutHittingRestTemplate() {
    // Force the proxy to register the circuit breaker instance first
    try {
      bankClient.authorize(buildRequest());
    } catch (Exception ignored) {
    }

    CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("bankCircuit");
    cb.transitionToOpenState();

    assertThatThrownBy(() -> bankClient.authorize(buildRequest()))
        .isInstanceOf(CallNotPermittedException.class)
        .hasMessageContaining("bankCircuit");

    // Only the forced init call hits RestTemplate, not the open-circuit call
    verify(restTemplate, times(1)).exchange(anyString(), eq(HttpMethod.POST), any(),
        eq(BankResponse.class));
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private PostPaymentRequest buildRequest() {
    PostPaymentRequest request = new PostPaymentRequest();
    request.setAmount(100);
    request.setCurrency("USD");
    request.setCardNumberLastFour(4111);
    request.setExpiryMonth(12);
    request.setExpiryYear(2026);
    request.setCvv("123");
    return request;
  }
}
