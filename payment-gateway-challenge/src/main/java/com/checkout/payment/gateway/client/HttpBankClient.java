package com.checkout.payment.gateway.client;

import com.checkout.payment.gateway.exception.BankRequestException;
import com.checkout.payment.gateway.exception.BankServiceUnavailableException;
import com.checkout.payment.gateway.model.BankResponse;
import com.checkout.payment.gateway.model.PostPaymentRequest;
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

  private final RestTemplate restTemplate;

  public HttpBankClient(RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }

  @Override
  public BankResponse authorize(PostPaymentRequest request) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    HttpEntity<PostPaymentRequest> httpEntity = new HttpEntity<>(request, headers);

    try {
      ResponseEntity<BankResponse> response = restTemplate.exchange(
          "http://localhost:8080/payments", HttpMethod.POST, httpEntity, BankResponse.class);
      return response.getBody();
    } catch (HttpClientErrorException.BadRequest e) {
      throw new BankRequestException(
          "Bank rejected request - missing required fields: " + e.getResponseBodyAsString());
    } catch (HttpServerErrorException.ServiceUnavailable e) {
      throw new BankServiceUnavailableException("Bank service unavailable");
    } catch (HttpStatusCodeException e) {
      throw new BankRequestException("Unexpected response from bank: " + e.getStatusCode().value());
    }
  }
}