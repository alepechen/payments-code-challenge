package com.checkout.payment.gateway.client;

import com.checkout.payment.gateway.exception.BankRequestException;
import com.checkout.payment.gateway.exception.BankServiceUnavailableException;
import com.checkout.payment.gateway.model.BankResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

@Component
public class HttpBankClient implements BankClient {
  private static final Logger LOG = LoggerFactory.getLogger(HttpBankClient.class);
  private final RestTemplate restTemplate;
  private final String bankBaseUrl;

  public HttpBankClient(RestTemplate restTemplate,@Value("${bank.base-url}") String bankBaseUrl) {
    this.restTemplate = restTemplate;
    this.bankBaseUrl = bankBaseUrl;
  }

  @Retry(name = "bankRetry")
  @CircuitBreaker(name = "bankCircuit")
  public BankResponse authorize(PostPaymentRequest request) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<PostPaymentRequest> httpEntity = new HttpEntity<>(request, headers);
    try {
      ResponseEntity<BankResponse> response = restTemplate.exchange(
          bankBaseUrl + "/payments", HttpMethod.POST, httpEntity, BankResponse.class);

      return response.getBody();
    } catch (HttpClientErrorException.BadRequest e) {
      // client error — don't retry
      throw new BankRequestException(
          "Bank rejected request: " + e.getResponseBodyAsString());
    } catch (HttpServerErrorException.ServiceUnavailable e) {
      // 503 — retryable, counts toward circuit breaker
      throw new BankServiceUnavailableException("Bank service unavailable");
    } catch (HttpStatusCodeException e) {
      throw new BankRequestException("Unexpected response from bank: " + e.getStatusCode().value());
    }
  }

  private BankResponse bankFallback(PostPaymentRequest request, Throwable t) {
    // called when circuit breaker is open or retries exhausted
    LOG.error("Bank service failed: {}", t.getMessage());
    throw new BankServiceUnavailableException("Bank service unavailable after retries");
  }
}
